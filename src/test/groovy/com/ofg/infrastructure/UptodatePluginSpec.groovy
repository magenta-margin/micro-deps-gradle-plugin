package com.ofg.infrastructure
import com.ofg.infrastructure.http.WireMockSpec
import org.codehaus.groovy.runtime.StackTraceUtils
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.ofg.infrastructure.StubRunnerPlugin.MOCK_DEPS_CONFIGURATION_NAME
import static com.ofg.infrastructure.StubRunnerPlugin.RUN_MOCKS_TASK_NAME
import static com.ofg.infrastructure.StubRunnerPlugin.STOP_MOCKS_TASK_NAME

class UptodatePluginSpec extends WireMockSpec {

    private static final Integer OK_STATUS = 200
    protected static final String TEST_PROJECT_ROOT_PATH = UptodatePluginSpec.class.getResource('/').file
    protected static final File TEST_PROJECT_ROOT = new File(TEST_PROJECT_ROOT_PATH)

    Project project = ProjectBuilder.builder().withProjectDir(TEST_PROJECT_ROOT).build()
    LoggerProxy loggerProxy = Mock()
    CommandExecutor commandExecutor = Mock()
    MicroserviceConfigurationFinder configurationFinder = new MicroserviceConfigurationFinder()
    DependenciesFinder dependenciesFinder = Mock()
    StubRunnerPlugin plugin = new StubRunnerPlugin(loggerProxy, commandExecutor, configurationFinder, dependenciesFinder)

    def setup() {
        plugin.apply(project)
        project.extensions.stubRunner.zookeeperPort = getHttpServerPort()
    }

    def "should send a request to /stop url when executing stopMocks task"() {
        given:
            stubInteraction(get(urlEqualTo('/stop')), aResponse().withStatus(OK_STATUS))
        when:
            executeStopMocksTask()
        then:
            wireMock.verifyThat(getRequestedFor(urlEqualTo('/stop')))
    }

    def "should execute 'java -jar ...' command when executing runMocks task"() {
        given:
            File microserviceConfigurationFile = new File("$TEST_PROJECT_ROOT_PATH/src/main/resources/microservice.json")
            String microDepsFatJarName = 'micro-deps-fat-jar.jar'
            Integer zookeeperPort = getHttpServerPort()
            Integer serviceStoppingPort = 12345
            String stubContainingRepositoryUrl = 'http://localhost/url'
        and:
            project.extensions.stubRunner.serviceStoppingPort = serviceStoppingPort
            project.extensions.stubRunner.stubContainingRepositoryUrl = stubContainingRepositoryUrl
            dependenciesFinder.getMicroDepsFatJarName(project) >> microDepsFatJarName
        when:
            executeRunMocksTask()
        then:
            1 * commandExecutor.execute({ it == "java -jar $microDepsFatJarName -p $zookeeperPort -mp $serviceStoppingPort -f ${microserviceConfigurationFile.absolutePath} -r $stubContainingRepositoryUrl"})
    }

    def "should throw exception if wrong params are passed when executing runMocks task"() {
        when:
            executeRunMocksTask()
        then:
            Throwable thrownException = thrown()
            StackTraceUtils.extractRootCause(thrownException).class == WrongMicroDepsExecutionParams
    }


    def 'should have mockDependencies configuration created'() {
        expect:
            project.configurations.getByName(MOCK_DEPS_CONFIGURATION_NAME)
    }

    private void executeStopMocksTask() {
        project.tasks.getByName(STOP_MOCKS_TASK_NAME).execute()
    }

    private void executeRunMocksTask() {
        project.tasks.getByName(RUN_MOCKS_TASK_NAME).execute()
    }

}
