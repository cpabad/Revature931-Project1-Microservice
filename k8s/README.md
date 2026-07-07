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
| | Secret | `AUTH_DB_PASSWORD`, `ERS_JWT_SECRET` |
| | Deployment | the auth-service pod; `envFrom` injects the ConfigMap + Secret as env vars |
| | Service | in-cluster DNS name `auth-service` |
| `30-ingress.yaml` | Ingress | exposes it outside the cluster via K3s's bundled Traefik |

## Prerequisites (not yet installed on this machine)

```bash
# 1. K3s (installs kubectl too). Single command, single binary:
curl -sfL https://get.k3s.io | sh -
# make kubectl usable without sudo:
mkdir -p ~/.kube && sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config && sudo chown $USER ~/.kube/config

# 2. The auth-service image. Until GitLab's registry is live (see ROADMAP), build locally and
#    import into K3s's own containerd so no registry is needed:
JAVA_HOME=~/jdks/jdk-21.0.11+10 mvn -pl auth-service -am package -DskipTests
docker build --build-arg MODULE=auth-service -t ers-auth-service:local .   # needs Docker
docker save ers-auth-service:local | sudo k3s ctr images import -
#    then set image: ers-auth-service:local in 20-auth-service.yaml (and imagePullPolicy stays IfNotPresent)
```

## Deploy — raw manifests

```bash
kubectl apply -f k8s/auth-service/00-namespace.yaml
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

## Copying this for Hexapawn

The template is deliberately one service + one database. For a simplified Hexapawn:
- drop the database objects entirely if the game holds no server-side state (many don't), leaving
  just the ConfigMap + Deployment + Service + Ingress for the game backend;
- swap the image, the port, and the env vars;
- keep the namespace/Service/Ingress shape exactly — that's the reusable Kubernetes muscle memory.

## Status

**Written, not yet run** — K3s/kubectl/tofu are not installed on the dev box (same honest caveat
as the Docker layer). The manifests are YAML-validated and the Tofu is structurally complete;
first `kubectl apply` / `tofu apply` on a K3s-equipped machine is the remaining step.
