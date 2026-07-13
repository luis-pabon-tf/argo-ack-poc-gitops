# Pinned versions for this POC

Recorded as each component was installed. All are deliberate pins — review before reusing any of this outside the POC.

| Component | Version | Source |
|---|---|---|
| kind | 0.32.0 | local tool |
| kind node image | `kindest/node:v1.36.1` | kind default for this kind version |
| Kubernetes (cluster) | v1.36.1 | via kind node image |
| Helm | v4.2.3 | local tool |
| LocalStack | `localstack/localstack:2026.06.2` | https://hub.docker.com/r/localstack/localstack/tags |
| ACK lambda-controller (Helm chart) | `oci://public.ecr.aws/aws-controllers-k8s/lambda-chart:1.14.1` | https://github.com/aws-controllers-k8s/lambda-controller/releases |
| Argo CD (Helm chart) | `argo/argo-cd:10.1.3` (app v3.4.5) | https://github.com/argoproj/argo-helm/releases |
| Jenkins | `jenkins/jenkins:2.568.1-lts-jdk21` | https://www.jenkins.io/changelog-stable/ |
| Jenkins plugins | unpinned in `plugins.txt`, resolved to whatever was current/compatible with 2.568.1 at image-build time — see the built image for exact installed versions if reproducing later | jenkins-plugin-cli dependency resolution |

Note: the ACK **ecs-controller** and Argo CD **Image Updater** chart were originally
planned (see git history of the build plan) but are **not** part of the final build —
see `NOTES.md` for why (LocalStack free-tier licensing pivoted the POC from ECS to
Lambda, and from container-image to Zip-packaged functions).
