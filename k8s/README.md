# Kubernetes (K3s) — the auth-service slice

A minimal, heavily-commented deployment of **one service + its database** on
[K3s](https://k3s.io) (a lightweight, single-binary Kubernetes). It's the copyable template:
learn the object types here on the smallest possible surface, then repeat the pattern for the
other ERS services — or for a Hexapawn service.

Two equivalent ways to deploy the *same* objects:
- **`k8s/auth-service/*.yaml`** — raw manifests, applied with `kubectl`.
- **`opentofu/`** — the same objects as infrastructure-as-code (`tofu plan` / `apply` / `destroy`).

Do one or the other, not both (they'd fight over the same objects).

## The objects, and why each exists

| File / resource | Kind | Job |
|---|---|---|
| `00-namespace.yaml` | Namespace | one blast-radius boundary for the whole app (`kubectl -n ers`) |
| `10-postgres.yaml` | Secret | the DB password, out of the Deployment spec |
| | PersistentVolumeClaim | durable disk (survives pod restarts) via K3s's local-path provisioner |
| | Deployment | the Postgres pod (`Recreate` strategy — two pods can't share one RWO disk) |
| | Service | stable in-cluster DNS name `auth-db` — what the app connects to |
| `20-auth-service.yaml` | ConfigMap | non-secret config (`AUTH_DB_URL`, `AUTH_DB_USER`) |
| | Secret | `AUTH_DB_PASSWORD` (the RS256 signing key rides in its own out-of-band Secret, below) |
| | Deployment | the auth-service pod; `envFrom` injects the ConfigMap + Secret; mounts `auth-jwt-key` |
| | Service | in-cluster DNS name `auth-service` |
| `30-ingress.yaml` | Ingress | exposes it outside the cluster via K3s's bundled Traefik |

## Prerequisites

```bash
# 1. K3s (installs kubectl too), if not already running. Single command, single binary:
curl -sfL https://get.k3s.io | sh -
# make kubectl usable without sudo:
mkdir -p ~/.kube && sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config && sudo chown $USER ~/.kube/config

# 2. The auth-service image, pushed to the GitLab Container Registry:
docker build --build-arg MODULE=auth-service \
  -t registry.gitlab.com/<namespace>/<project>/auth-service:latest .
docker push registry.gitlab.com/<namespace>/<project>/auth-service:latest
#    Registryless local alternative (no registry, no pull secret): build, import into K3s's own
#    containerd, and set image: ers-auth-service:local in 20-auth-service.yaml —
#      docker build --build-arg MODULE=auth-service -t ers-auth-service:local .
#      docker save ers-auth-service:local | sudo k3s ctr images import -

# 3. The image-pull secret (a GitLab deploy token, scope read_registry) — created out-of-band,
#    never in a tracked file. Create it after the namespace exists (see the deploy step):
kubectl create secret docker-registry gitlab-registry --namespace ers \
  --docker-server=registry.gitlab.com \
  --docker-username="<deploy-token-username>" --docker-password="<deploy-token>"

# 4. The RS256 JWT signing key (PKCS#8 PEM) — also out-of-band, same discipline. All replicas
#    load this ONE key (kid = its thumbprint), so tokens survive restarts and scale-out:
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-signing.pem
kubectl -n ers create secret generic auth-jwt-key --from-file=jwt-signing.pem
```

## Deploy — raw manifests

```bash
kubectl apply -f k8s/auth-service/00-namespace.yaml
# both out-of-band secrets must exist before the auth-service pod can start (Prereqs 3 + 4):
kubectl create secret docker-registry gitlab-registry --namespace ers \
  --docker-server=registry.gitlab.com \
  --docker-username="<deploy-token-username>" --docker-password="<deploy-token>"
kubectl -n ers create secret generic auth-jwt-key --from-file=jwt-signing.pem
# the init SQL goes in as a ConfigMap from the real file (the idiomatic --from-file pattern):
kubectl -n ers create configmap auth-db-init --from-file=db/auth/init.sql
kubectl apply -f k8s/auth-service/          # the rest of the folder

kubectl -n ers get pods -w                  # watch them go Ready
echo "127.0.0.1 ers.local" | sudo tee -a /etc/hosts
curl -X POST http://ers.local/login -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"<seeded-pw>"}'
```

Tear down: `kubectl delete namespace ers` (removes everything in it).

## Deploy — OpenTofu

```bash
cd opentofu
tofu init
tofu plan       # shows exactly what will be created — read it, this is the payoff over kubectl
tofu apply
# ...
tofu destroy    # one command removes it all
```

The Tofu config reads `db/auth/init.sql` via `file()`, so the ConfigMap step is automatic there.
See `opentofu/README.md` for the pull-secret **two-beat apply** (namespace first, then the secret,
then the rest — `kubernetes_deployment` waits for rollout, which blocks until the secret exists).

## Copying this for Hexapawn

The template is deliberately one service + one database. For a simplified Hexapawn:
- drop the database objects entirely if the game holds no server-side state (many don't), leaving
  just the ConfigMap + Deployment + Service + Ingress for the game backend;
- swap the image, the port, and the env vars;
- keep the namespace/Service/Ingress shape exactly — that's the reusable Kubernetes muscle memory.

## Status

**Applied and verified green** on local K3s (v1.36.2) via the OpenTofu path: `tofu apply` brings up
`auth-db` + `auth-service` + the Traefik Ingress, the image pulls from the GitLab registry through
the `gitlab-registry` secret, and login as a seeded user returns a 200 + RS256 JWT through
`http://ers.local/login`. The raw `k8s/` manifests are the equivalent reference — deploy with one
path or the other, never both. See `opentofu/README.md` for the verified apply sequence.
