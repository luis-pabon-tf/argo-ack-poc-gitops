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

## 2026-07-14 — Gitops-repo PR is auto-merged, removing the original second human gate
The original build plan called for two separate human approval gates: code review on
`app-repo`, and a separate deploy approval on `gitops-repo` (its own PR merge). In
practice the gitops-repo PR only ever changes one field (`s3Key`) — it's a mechanical
"kick Argo CD" step, not a code-review decision, so a human clicking merge on it added
toil without adding judgment.

**Decision:** Jenkins now merges that PR itself, right after opening it, using the
same PAT it already had (fine-grained PAT scoped to Contents + Pull requests on both
repos — no scope broadened; merging a PR only needs Contents: write, already granted).
Checked first whether GitHub's native auto-merge queue was a better fit: it isn't,
since the repo's `allow_auto_merge` setting is off and turning it on requires an
Administration-scoped token, which would have meant broadening the PAT beyond what
it needs. A direct merge call avoids that.

**Safety net:** before pushing, Jenkins asserts the diff touches exactly one file
(`apps/sample-function/function.yaml`) and exactly one changed line pair, both sides
matching `s3Key:`. Anything else (a bug, a bad rebase, tampering) aborts the pipeline
loudly instead of silently auto-merging. This is what makes removing the human gate
here defensible — an unattended merge only ever lands the one deterministic bump it's
supposed to.

**What this trades away:** success criterion #1 in the build plan ("a single
human-reviewed PR merge on app-repo, followed by a single human-reviewed PR merge on
gitops-repo...") is no longer literally true — there's only one human-reviewed merge
now (app-repo). Traceability is preserved (every deploy still ties to a real, merged
PR on gitops-repo, per criterion #4), just not human-approved. This was an explicit,
discussed tradeoff, not something that happened silently.
