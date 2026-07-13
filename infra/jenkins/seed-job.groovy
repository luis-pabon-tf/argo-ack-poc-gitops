import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import hudson.plugins.git.GitSCM
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.UserRemoteConfig

def jenkins = Jenkins.get()
def jobName = "argo-ack-poc-app"

if (jenkins.getItem(jobName) == null) {
    def job = jenkins.createProject(WorkflowJob.class, jobName)
    def remoteConfigs = [new UserRemoteConfig("https://github.com/luis-pabon-tf/argo-ack-poc-app.git", null, null, "gitops-pat-checkout")]
    def scm = new GitSCM(remoteConfigs, [new BranchSpec("*/main")], false, [], null, null, [])
    def flowDef = new CpsScmFlowDefinition(scm, "Jenkinsfile")
    flowDef.setLightweight(true)
    job.setDefinition(flowDef)
    job.save()
    jenkins.save()
    println("Seeded pipeline job: ${jobName}")
} else {
    println("Pipeline job ${jobName} already exists, skipping seed")
}
