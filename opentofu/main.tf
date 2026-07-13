# ============================================================================
# OpenTofu: the auth-service slice as infrastructure-as-code, declared against a
# LOCAL K3s cluster via the kubernetes provider. Same objects as k8s/auth-service/*.yaml,
# but now `tofu plan` shows a diff before you touch the cluster and `tofu destroy` cleans
# up in one command — the whole point of IaC over kubectl apply.
#
# This is the pattern to copy for Hexapawn: one variables block, one namespace, a database
# module-shaped block, and an app block. Swap the image + env and it's a new service.
# ============================================================================

terraform {
  required_version = ">= 1.6"
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.30"
    }
  }
}

# Talk to K3s. K3s writes its kubeconfig to /etc/rancher/k3s/k3s.yaml (root-owned) — copy it to
# ~/.kube/config or point KUBE_CONFIG_PATH at it. See opentofu/README.md.
provider "kubernetes" {
  config_path = var.kubeconfig
}

variable "kubeconfig" {
  description = "Path to the K3s kubeconfig"
  type        = string
  default     = "~/.kube/config"
}

variable "namespace" {
  type    = string
  default = "ers"
}

variable "db_password" {
  description = "Postgres password for ers_auth"
  type        = string
  default     = "ers"
  sensitive   = true
}

# No jwt_secret variable: auth-service signs RS256 with an EPHEMERAL RSA keypair generated at
# startup (see JwkConfig) and serves the public half at /.well-known/jwks.json. There is no
# signing secret to declare here. A JWKS verifier URI arrives with the full stack, not this slice.

variable "auth_image" {
  description = "auth-service image in the GitLab Container Registry (project ca132731/Revature931-Project1-Microservice)"
  type        = string
  default     = "registry.gitlab.com/ca132731/revature931-project1-microservice/auth-service:latest"
}

resource "kubernetes_namespace" "ers" {
  metadata {
    name = var.namespace
  }
}

# ---- database: secret, init configmap (from the real file), PVC, deployment, service ----------

resource "kubernetes_secret" "auth_db" {
  metadata {
    name      = "auth-db-secret"
    namespace = kubernetes_namespace.ers.metadata[0].name
  }
  data = {
    POSTGRES_PASSWORD = var.db_password
  }
}

resource "kubernetes_config_map" "auth_db_init" {
  metadata {
    name      = "auth-db-init"
    namespace = kubernetes_namespace.ers.metadata[0].name
  }
  # file() reads the SAME init script docker-compose and local dev use — one source of truth.
  data = {
    "init.sql" = file("${path.module}/../db/auth/init.sql")
  }
}

resource "kubernetes_persistent_volume_claim" "auth_db" {
  metadata {
    name      = "auth-db-data"
    namespace = kubernetes_namespace.ers.metadata[0].name
  }
  spec {
    access_modes = ["ReadWriteOnce"]
    resources {
      requests = {
        storage = "1Gi"
      }
    }
  }
  # Don't block apply waiting for a Pod to bind the volume (local-path binds lazily, on first use).
  wait_until_bound = false
}

resource "kubernetes_deployment" "auth_db" {
  metadata {
    name      = "auth-db"
    namespace = kubernetes_namespace.ers.metadata[0].name
  }
  spec {
    replicas = 1
    strategy {
      type = "Recreate"
    }
    selector {
      match_labels = { app = "auth-db" }
    }
    template {
      metadata {
        labels = { app = "auth-db" }
      }
      spec {
        container {
          name  = "postgres"
          image = "postgres:16-alpine"
          port {
            container_port = 5432
          }
          env {
            name  = "POSTGRES_DB"
            value = "ers_auth"
          }
          env {
            name  = "POSTGRES_USER"
            value = "ers"
          }
          env {
            name = "POSTGRES_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.auth_db.metadata[0].name
                key  = "POSTGRES_PASSWORD"
              }
            }
          }
          env {
            name  = "PGDATA"
            value = "/var/lib/postgresql/data/pgdata"
          }
          volume_mount {
            name       = "data"
            mount_path = "/var/lib/postgresql/data"
          }
          volume_mount {
            name       = "init"
            mount_path = "/docker-entrypoint-initdb.d"
            read_only  = true
          }
          readiness_probe {
            exec {
              command = ["pg_isready", "-U", "ers", "-d", "ers_auth"]
            }
            initial_delay_seconds = 5
            period_seconds        = 5
          }
        }
        volume {
          name = "data"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim.auth_db.metadata[0].name
          }
        }
        volume {
          name = "init"
          config_map {
            name = kubernetes_config_map.auth_db_init.metadata[0].name
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "auth_db" {
  metadata {
    name      = "auth-db"
    namespace = kubernetes_namespace.ers.metadata[0].name
  }
  spec {
    selector = { app = "auth-db" }
    port {
      port        = 5432
      target_port = 5432
    }
  }
}

# ---- app: config, secret, deployment, service ---------------------------------------------------

resource "kubernetes_config_map" "auth_service" {
  metadata {
    name      = "auth-service-config"
    namespace = kubernetes_namespace.ers.metadata[0].name
  }
  data = {
    AUTH_DB_URL  = "jdbc:postgresql://auth-db:5432/ers_auth"
    AUTH_DB_USER = "ers"
  }
}

resource "kubernetes_secret" "auth_service" {
  metadata {
    name      = "auth-service-secret"
    namespace = kubernetes_namespace.ers.metadata[0].name
  }
  data = {
    AUTH_DB_PASSWORD = var.db_password
  }
}

resource "kubernetes_deployment" "auth_service" {
  metadata {
    name      = "auth-service"
    namespace = kubernetes_namespace.ers.metadata[0].name
  }
  spec {
    replicas = 1
    selector {
      match_labels = { app = "auth-service" }
    }
    template {
      metadata {
        labels = { app = "auth-service" }
      }
      spec {
        # Pull the private image from the GitLab Container Registry. The gitlab-registry secret is
        # created out-of-band (kubectl create secret docker-registry ...) so no token lands in Tofu.
        image_pull_secrets {
          name = "gitlab-registry"
        }
        container {
          name  = "auth-service"
          image = var.auth_image
          # Always, because the tag is mutable (:latest): IfNotPresent would keep serving the
          # node's cached build forever after a re-push. Cheap when unchanged (digest check).
          # Pinning by digest/version tag would make IfNotPresent correct again.
          image_pull_policy = "Always"
          port {
            container_port = 8081
          }
          env_from {
            config_map_ref {
              name = kubernetes_config_map.auth_service.metadata[0].name
            }
          }
          env_from {
            secret_ref {
              name = kubernetes_secret.auth_service.metadata[0].name
            }
          }
          # Points JwkConfig at the mounted signing key (volume below). Every replica loads the
          # SAME RS256 pair (kid = key thumbprint), so tokens survive restarts and scale-out;
          # an unloadable path fails the boot rather than downgrading to per-replica keys.
          env {
            name  = "ERS_JWT_PRIVATE_KEY_PEM"
            value = "/etc/ers/keys/jwt-signing.pem"
          }
          volume_mount {
            name       = "jwt-signing-key"
            mount_path = "/etc/ers/keys"
            read_only  = true
          }
          readiness_probe {
            tcp_socket {
              port = 8081
            }
            initial_delay_seconds = 20
            period_seconds        = 10
          }
          liveness_probe {
            tcp_socket {
              port = 8081
            }
            initial_delay_seconds = 40
            period_seconds        = 15
          }
          resources {
            requests = {
              cpu    = "100m"
              memory = "384Mi"
            }
            limits = {
              memory = "512Mi"
            }
          }
        }
        # The RS256 signing key - a PKCS#8 PEM in a Secret created OUT-OF-BAND like the
        # gitlab-registry pull secret (a private signing key never belongs in git OR tfstate):
        #   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-signing.pem
        #   kubectl -n ers create secret generic auth-jwt-key --from-file=jwt-signing.pem
        # Deliberately NOT optional: the pod waits until the secret exists (create it in the
        # same beat as gitlab-registry) instead of booting with a per-replica ephemeral key.
        volume {
          name = "jwt-signing-key"
          secret {
            secret_name = "auth-jwt-key"
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "auth_service" {
  metadata {
    name      = "auth-service"
    namespace = kubernetes_namespace.ers.metadata[0].name
  }
  spec {
    selector = { app = "auth-service" }
    port {
      port        = 8081
      target_port = 8081
    }
  }
}

# ---- ingress: expose the slice OUTSIDE the cluster via K3s's bundled Traefik ---------------------
# Parity with k8s/auth-service/30-ingress.yaml — the raw-manifest path had an Ingress but the Tofu
# path did not, so `tofu apply` alone could not serve http://ers.local/login. This closes that gap.
# K3s ships Traefik as its default IngressController, so no controller install is needed; add
# `127.0.0.1 ers.local` to /etc/hosts and the host resolves locally. When the full stack lands, the
# gateway becomes the single backend behind "/" and the per-service routes move behind it.
resource "kubernetes_ingress_v1" "ers" {
  metadata {
    name      = "ers-ingress"
    namespace = kubernetes_namespace.ers.metadata[0].name
  }
  spec {
    rule {
      host = "ers.local"
      http {
        path {
          path      = "/"
          path_type = "Prefix"
          backend {
            service {
              name = kubernetes_service.auth_service.metadata[0].name
              port {
                number = 8081
              }
            }
          }
        }
      }
    }
  }
}
