# OpenTofu — the auth-service slice as infrastructure-as-code

The **same objects** as `k8s/auth-service/*.yaml`, declared in HCL against a local
[K3s](https://k3s.io) cluster via the `kubernetes` provider. The payoff over `kubectl apply`:
`tofu plan` shows a diff *before* anything touches the cluster, and `tofu destroy` removes the
whole slice in one command. `main.tf` is heavily commented — read it alongside this.

> **Pick ONE path.** Deploy with `opentofu/` **or** with the raw `k8s/` manifests, never both —
> they create the same namespaced objects and would fight over ownership. See `k8s/README.md`.

## What it creates (11 resources)

Namespace `ers`, then the database half (Secret, init-SQL ConfigMap read from `db/auth/init.sql`
via `file()`, PVC, Deployment `postgres:16-alpine`, Service `auth-db`), then the app half
(ConfigMap, Secret — `AUTH_DB_PASSWORD` only, **no JWT secret**), Deployment (your registry image
+ the `gitlab-registry` pull secret), Service `auth-service`), then a Traefik `Ingress` for
`ers.local`. `tofu plan` prints the full list.

## Prerequisites

1. **A running K3s cluster** and a kubeconfig you can read without `sudo`:
   ```bash
   mkdir -p ~/.kube && sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config && sudo chown $USER ~/.kube/config
   ```
   The provider reads `~/.kube/config` by default (override with `-var kubeconfig=...` or the
   `KUBE_CONFIG_PATH` env var). Pass an **absolute** path — the provider does not always expand `~`.

2. **The auth-service image pushed to the GitLab Container Registry**:
   ```bash
   docker build --build-arg MODULE=auth-service \
     -t registry.gitlab.com/<namespace>/<project>/auth-service:latest .
   docker push  registry.gitlab.com/<namespace>/<project>/auth-service:latest
   ```
   Set `-var auth_image=...` (or edit the `auth_image` default) to match your registry path.

3. **The image-pull secret** — the private image cannot be pulled without it. This secret is
   created **out-of-band on purpose**: a GitLab deploy token (scope `read_registry`) must never
   land in a tracked file or in Tofu state. Create it *after* the namespace exists (next section):
   ```bash
   kubectl create secret docker-registry gitlab-registry --namespace ers \
     --docker-server=registry.gitlab.com \
     --docker-username="<deploy-token-username>" --docker-password="<deploy-token>"
   ```

4. **The RS256 JWT signing key** — same out-of-band discipline (a private signing key in Tofu
   state would be readable by anyone with the state file). All replicas mount this ONE key, so
   tokens survive pod restarts and scale-out; the kid is the key's thumbprint:
   ```bash
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-signing.pem
   kubectl -n ers create secret generic auth-jwt-key --from-file=jwt-signing.pem
   ```

## Apply — the two-beat sequence

`kubernetes_deployment` **waits for rollout**, and the pod cannot roll out until the pull secret
exists. If you apply everything at once before creating the secret, the apply blocks on
`ImagePullBackOff` until it times out. So apply the namespace first, create the secret into it,
then apply the rest:

```bash
cd opentofu
tofu init

# 1. namespace only, so there is somewhere to put the secret
tofu apply -target=kubernetes_namespace.ers

# 2. create BOTH out-of-band secrets (Prerequisites 3 + 4) into the now-existing ers namespace
#    kubectl create secret docker-registry gitlab-registry --namespace ers ...
#    kubectl -n ers create secret generic auth-jwt-key --from-file=jwt-signing.pem

# 3. the rest — read the plan diff first (the whole point over kubectl), then apply
tofu plan            # 10 resources to add; secret data shows as (sensitive value)
tofu apply
```

Once the full stack is in the cluster and the gateway becomes the single Ingress backend, this
dance goes away for downstream services — the secret is a one-time, per-namespace setup.

## Verify login through the Traefik Ingress

```bash
# ers.local must resolve locally:  echo "127.0.0.1 ers.local" | sudo tee -a /etc/hosts
curl -s http://ers.local/.well-known/jwks.json            # 200 + an RSA public key (kid)
curl -i -X POST http://ers.local/login -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"<seeded-pw>"}'   # 200 + a Bearer JWT (alg RS256)
```
A `200` with `token` / `tokenType: Bearer` / `userId` / `role`, whose JWT header `kid` matches the
`kid` from the JWKS endpoint, is the green light: one in-memory keypair both signs the token and
publishes its public half — no shared secret in the system.

## Tear down

```bash
tofu destroy               # removes the 10 Tofu-managed objects
kubectl -n ers delete secret gitlab-registry auth-jwt-key   # the out-of-band secrets Tofu does not own
```

## Variables

| Variable | Default | Notes |
|---|---|---|
| `kubeconfig` | `~/.kube/config` | pass an absolute path |
| `namespace` | `ers` | |
| `db_password` | `ers` | dev default; lands in a k8s Secret, never a tracked file |
| `auth_image` | `registry.gitlab.com/ca132731/revature931-project1-microservice/auth-service:latest` | your pushed image |

There is deliberately **no `jwt_secret`** variable: auth-service signs RS256 and serves the public
half at `/.well-known/jwks.json`. In this deployment the keypair is a PKCS#8 PEM mounted from the
out-of-band `auth-jwt-key` Secret (Prerequisite 4) — persistent across restarts and shared by all
replicas, never in git or Tofu state. Without the mount (bare local runs) the service generates an
ephemeral pair instead, which is single-replica only. A JWKS verifier URI arrives with the full
stack, not this single-service slice.

## Status

**Applied and verified green** on local K3s (v1.36.2): `tofu apply` brings up auth-db +
auth-service + Ingress; the image pulls from the GitLab registry via the `gitlab-registry` secret;
login as a seeded user returns a 200 + RS256 JWT through `http://ers.local/login`. This is the
copyable pattern — swap the image, port, and env for the next ERS service or a Hexapawn backend.
