# Running notes — divergences from the real target environment

## 2026-07-13 — ECS unavailable on LocalStack free tier; pivoted to Lambda
LocalStack's free Hobby plan (aka the `freemium` license tier) excludes ECS and ECR.
Confirmed via container logs: `plugin localstack.aws.provider:ecs:pro is disabled,
reason: This feature is not part of the active license agreement`. This held true even
after regenerating the auth token post-upgrade to Hobby — Hobby and freemium are the
same tier; ECS requires a paid Base plan ($39+/mo) or higher.

**Decision:** pivoted the POC to target AWS Lambda (via ACK `lambda-controller`)
instead of ECS. The build plan (`argocd-ack-poc-build-plan.md`) was updated accordingly.
This still proves every mechanic the POC cares about (PR-gated automation, single
credential holder, drift self-correction); only the specific ACK controller/CRD differs.
ECS-specific behavior (task definition revisioning, service updates) is unverified by
this POC and would need a paid LocalStack plan or a real AWS sandbox to check later.

## 2026-07-13 — Container-image Lambda functions also unavailable on the free tier
Verified directly: `awslocal lambda create-function --package-type Image` against a
local Docker image succeeds at creation time, but invocation fails with
`NotImplementedError: Container images are a Pro feature`.

**Decision:** using **Zip-packaged** Lambda functions instead (deployment package
uploaded to S3, referenced by bucket/key in the ACK `Function` CR). Verified this path
works end-to-end (create → Active → invoke → correct response) with a throwaway test
function before building further.

**Consequence for Phase 7:** Argo CD Image Updater watches container registry tags —
with a Zip-packaged function there is no image tag to watch. Per the build plan's own
fallback, Jenkins will open the `gitops-repo` PR directly (via the GitHub API, same PAT
scoped to PR-open-only) after uploading the new zip to S3, bumping the S3 object
key/version in the `Function` CR. This preserves PR-gated traceability without
Image Updater in the loop. Argo CD Image Updater will not be installed for this POC —
noting this so it isn't mistaken for an oversight later.

## 2026-07-13 — Jenkins triggers on SCM polling, not a GitHub webhook
Jenkins runs as a local Docker container with no public ingress, so GitHub cannot
deliver a webhook to it. Using `pollSCM('* * * * *')` (checks app-repo every minute)
as the local substitution. In the real environment this would be a webhook-triggered
build instead — polling is a POC-only workaround, not something to carry forward.
