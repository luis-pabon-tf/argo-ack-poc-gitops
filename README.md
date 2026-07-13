# argo-ack-poc-gitops

GitOps repo for the Argo CD + ACK GitOps POC. Tracked by the `sample-function` Argo CD
Application (`argocd/application.yaml`), which syncs `apps/sample-function/function.yaml`
(an ACK `lambda.services.k8s.aws/v1alpha1` `Function` custom resource) to the cluster.

The ACK `lambda-controller` reads the `Function` CR and reconciles it against
LocalStack's emulated Lambda API. `S3Key` in `function.yaml` is bumped by a PR opened
by the `argo-ack-poc-app` Jenkins pipeline on every merge to that repo's `main` branch.
