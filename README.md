# argo-ack-poc-gitops

GitOps repo for a local, free proof-of-concept of an Argo CD + ACK GitOps pipeline. It
proves the pipeline **mechanics** — PR-gated automation, a single credential-holding
component, drift self-correction, full commit-to-running-service traceability — using
free, local tooling standing in for a real (and separately, FedRAMP/GovCloud-bound) AWS
environment. See `NOTES.md` for the running log of decisions and divergences made along
the way, and `VERSIONS.md` for every pinned tool/chart/image version.

## What this proves, and on what

The original plan targeted ECS. Partway through, LocalStack's free Hobby plan turned out
to exclude both ECS/ECR and container-image-packaged Lambda functions (confirmed by
testing directly against a running LocalStack instance, not assumed from docs — see
`NOTES.md`). The POC pivoted to **AWS Lambda, Zip-packaged**, via the ACK
`lambda-controller`. Every property this POC set out to prove applies identically
whether ACK is driving ECS or Lambda; only the specific ACK controller/CRD differs.
ECS-specific behavior is explicitly **not** proven here (see "Divergences" below).

## Repos involved

- [argo-ack-poc-app](https://github.com/luis-pabon-tf/argo-ack-poc-app) — the app repo.
  A trivial Lambda handler + a `Jenkinsfile`.
- **argo-ack-poc-gitops** (this repo) — `apps/sample-function/function.yaml` (the ACK
  `Function` CR) and `argocd/application.yaml` (the Argo CD `Application`), plus
  `infra/` holding everything needed to stand the rest of the environment back up.

## Architecture (as built)

```
Dev PR (app-repo) → human merge
    → Jenkins (SCM-polling trigger, no webhook reachable locally)
        → zips handler.py, uploads to LocalStack S3 (poc-lambda-artifacts)
        → opens a PR against gitops-repo bumping Function.spec.code.s3Key
    → human merge (2nd approval gate — the deploy gate)
    → Argo CD auto-syncs gitops-repo → cluster (selfHeal: true, prune: true)
    → ACK lambda-controller (only component holding AWS-equivalent credentials)
        reconciles the Function CR against LocalStack's Lambda API
    → invoking the function reflects the change
```

## Standing the whole environment up from scratch

Prerequisites: Docker (running, ~8GB RAM available), `kubectl`, `helm`, `git`, `kind`,
a LocalStack account on the free Hobby plan, and a GitHub account with a fine-grained
PAT scoped to just the two POC repos (Contents: Read & Write, Pull requests: Read &
Write).

1. **Secrets.** Copy `infra/.env.example` to a local `.env` (never commit it) and fill
   in `LOCALSTACK_AUTH_TOKEN`, `GITOPS_PR_PAT`, and a `JENKINS_ADMIN_PASSWORD` of your
   choosing.

2. **Cluster.**
   ```
   kind create cluster --config infra/kind-config.yaml
   ```

3. **LocalStack.** Only Lambda, IAM, S3, STS, and logs are enabled — ECS/ECR are not
   available on the free tier, don't try to enable them (see `NOTES.md`).
   ```
   cd infra/localstack && docker compose --env-file ../../.env up -d
   ```
   It joins the `kind` Docker network as `localstack`, so in-cluster controllers can
   reach it at `http://localstack:4566`.

4. **ACK lambda-controller.**
   ```
   kubectl create namespace ack-system
   kubectl create secret generic ack-lambda-aws-creds --namespace ack-system \
     --from-literal=credentials=$'[default]\naws_access_key_id = test\naws_secret_access_key = test\n'
   helm install ack-lambda-controller oci://public.ecr.aws/aws-controllers-k8s/lambda-chart \
     --version 1.14.1 --namespace ack-system -f infra/ack-lambda-values.yaml
   ```

5. **Argo CD.**
   ```
   kubectl create namespace argocd
   helm repo add argo https://argoproj.github.io/argo-helm
   helm install argocd argo/argo-cd --version 10.1.3 --namespace argocd
   ```
   Register this repo as a credentialed source (substitute your real PAT for the
   placeholder — never commit the substituted file):
   ```
   sed "s|__PAT_PLACEHOLDER__|$GITOPS_PR_PAT|" infra/argocd/argocd-repo-secret.yaml | kubectl apply -f -
   kubectl apply -f argocd/application.yaml
   ```

6. **Jenkins.**
   ```
   cd infra/jenkins && docker compose --env-file ../../.env build && docker compose --env-file ../../.env up -d
   ```
   Configuration as Code seeds the `admin` user, the `gitops-pr-pat` (Secret text) and
   `gitops-pat-checkout` (Username/password) credentials, and a pipeline job pointed at
   `argo-ack-poc-app`'s `Jenkinsfile`, all from environment variables — no manual setup
   wizard. Jenkins UI: http://localhost:8081.

7. **Drive it.** Merge a PR on `argo-ack-poc-app` → within a minute Jenkins builds and
   opens a PR here → merge that → Argo CD syncs → ACK reconciles → the function is live.

## Teardown

```
cd infra/jenkins && docker compose --env-file ../../.env down -v
cd ../localstack && docker compose --env-file ../../.env down -v
kind delete cluster --name gitops-poc
```

## Divergences from the real, FedRAMP/GovCloud-bound build

Full detail and reasoning in `NOTES.md`; summary:

- **Targets Lambda, not ECS.** LocalStack's free Hobby plan excludes ECS/ECR. ECS-specific
  behavior (task definition revisioning, service updates) is unverified here — proving it
  would need a paid LocalStack Base plan or a real AWS sandbox.
- **Zip-packaged, not container-image Lambda.** Container-image Lambda invocation is also
  a LocalStack Pro-only feature; confirmed by testing, not assumed.
- **No Argo CD Image Updater.** With no container registry in the loop, there's no image
  tag to watch. Jenkins opens the gitops PR directly instead — functionally equivalent,
  but a different component than the plan originally called for.
- **Jenkins triggers on SCM polling, not a webhook.** Jenkins has no public ingress
  locally; polling every minute substitutes for what would be a webhook in the real
  environment.
- **No real IAM enforcement.** LocalStack doesn't enforce AWS's actual IAM permission
  model — a role that looks correctly scoped here can still be wrong against real AWS.
- **No real networking.** Not exercised by a Lambda-based POC in any case.
- **LocalStack Hobby plan requires a free account** (not a purely offline/anonymous
  Docker pull) and is for non-commercial use only.
- **Jenkins config is minimal, not hardened** — no HTTPS, a single admin user, no RBAC
  beyond "logged-in users can do anything." Fine for a local POC, not for anything
  beyond it.
- **All image/chart versions are pinned for this POC specifically** (see
  `VERSIONS.md`) and are due for a deliberate review before any reuse.
- **This POC runs entirely outside any compliance boundary** and proves pipeline
  mechanics only, not GovCloud/FedRAMP compliance.
