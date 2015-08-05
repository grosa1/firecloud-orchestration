package org.broadinstitute.dsde.firecloud.service

import org.broadinstitute.dsde.firecloud.mock.MockWorkspaceServer
import org.broadinstitute.dsde.firecloud.model.ModelJsonProtocol._
import org.broadinstitute.dsde.vault.common.openam.OpenAMSession
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FreeSpec, Matchers}
import spray.http.HttpHeaders.Cookie
import spray.http.StatusCodes._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.testkit.ScalatestRouteTest

class MethodConfigurationServiceSpec extends FreeSpec with ScalaFutures with ScalatestRouteTest with Matchers with MethodConfigurationService {

  def actorRefFactory = system

  private final val validUpdateMethodConfigUrl = s"/workspaces/%s/%s/method_configs/%s/%s".format(
    MockWorkspaceServer.mockValidWorkspace.namespace.get,
    MockWorkspaceServer.mockValidWorkspace.name.get,
    MockWorkspaceServer.mockValidWorkspace.namespace.get,
    MockWorkspaceServer.mockValidWorkspace.name.get)
  private final val validCopyFromRepoUrl = s"/workspaces/%s/%s/method_configs/copyFromMethodRepo".format(
    MockWorkspaceServer.mockValidWorkspace.namespace.get,
    MockWorkspaceServer.mockValidWorkspace.name.get
  )
  private final val openAMSession = OpenAMSession(()).futureValue(timeout(Span(5, Seconds)), interval(scaled(Span(0.5, Seconds))))
  private final val token = openAMSession.cookies.head.content
  private final val validConfigurationCopyFormData = FormData(Seq(
    "configurationNamespace" -> "config-ns",
    "configurationName" -> "config-name",
    "configurationSnapshot" -> "1",
    "destinationNamespace" -> "new-config-ns",
    "destinationName" -> "new-config-name"
  ))
  private final val invalidConfigurationCopyFormData = FormData(Seq("configurationNamespace" -> "config-ns"))

  override def beforeAll(): Unit = {
    MockWorkspaceServer.startWorkspaceServer()
  }

  override def afterAll(): Unit = {
    MockWorkspaceServer.stopWorkspaceServer()
  }

  "MethodConfigurationService" - {

    "when calling PUT on the /workspaces/*/*/method_configs/*/* path with a valid method configuration" - {
      "OK response is returned" in {
        Put(validUpdateMethodConfigUrl, MockWorkspaceServer.mockMethodConfigs.head) ~> Cookie(HttpCookie("iPlanetDirectoryPro", token)) ~> sealRoute(routes) ~> check {
          status should equal(OK)
        }
      }
    }

    "when calling PUT on the /workspaces/*/*/method_configs/*/* path with a nonexistent method configuration" - {
      "OK response is returned" in {
        Put(validUpdateMethodConfigUrl, MockWorkspaceServer.mockInvalidWorkspace) ~> Cookie(HttpCookie("iPlanetDirectoryPro", token)) ~> sealRoute(routes) ~> check {
          status should equal(NotFound)
        }
      }
    }

    "when calling GET on the /workspaces/*/*/method_configs/*/* path" - {
      "MethodNotAllowed error is returned" in {
        Get(validUpdateMethodConfigUrl) ~> Cookie(HttpCookie("iPlanetDirectoryPro", token)) ~> sealRoute(routes) ~> check {
          status should equal(MethodNotAllowed)
        }
      }
    }

    "when calling POST on the /workspaces/*/*/method_configs/*/* path" - {
      "MethodNotAllowed error is returned" in {
        Post(validUpdateMethodConfigUrl, MockWorkspaceServer.mockMethodConfigs.head) ~> Cookie(HttpCookie("iPlanetDirectoryPro", token)) ~> sealRoute(routes) ~> check {
          status should equal(MethodNotAllowed)
        }
      }
    }

    "when calling PUT on the /workspaces/*/*/method_configs/*/* path without a valid authentication token" - {
      "OK response is returned" in {
        Put(validUpdateMethodConfigUrl, MockWorkspaceServer.mockMethodConfigs.head) ~> sealRoute(routes) ~> check {
          status should equal(Unauthorized)
        }
      }
    }

    /**
     * This test will fail if used as an integration test. Integration testing requires an existing
     * configuration in Agora that is accessible to the current user and a valid workspace in Rawls.
     */
    "when calling POST on the /workspaces*/*/method_configs/copyFromMethodRepo path with valid workspace and configuration data" - {
      "Created response is returned" in {
        Post(validCopyFromRepoUrl, validConfigurationCopyFormData) ~> Cookie(HttpCookie("iPlanetDirectoryPro", token)) ~> sealRoute(routes) ~> check {
          status should equal(Created)
        }
      }
    }

    "when calling POST on the /workspaces*/*/method_configs/copyFromMethodRepo path with invalid data" - {
      "BadRequest response is returned" in {
        Post(validCopyFromRepoUrl, invalidConfigurationCopyFormData) ~> Cookie(HttpCookie("iPlanetDirectoryPro", token)) ~> sealRoute(routes) ~> check {
          status should equal(BadRequest)
        }
      }
    }

    "when calling POST on the /workspaces*/*/method_configs/copyFromMethodRepo path without a valid authentication token" - {
      "Found (302 redirect) response is returned" in {
        Post(validCopyFromRepoUrl, validConfigurationCopyFormData) ~> sealRoute(routes) ~> check {
          status should equal(Unauthorized)
        }
      }
    }

    "when calling GET on the /workspaces*/*/method_configs/copyFromMethodRepo path" - {
      "MethodNotAllowed error is returned" in {
        Get(validCopyFromRepoUrl) ~> sealRoute(routes) ~> check {
          status should equal(MethodNotAllowed)
          responseAs[String] === "HTTP method not allowed, supported methods: GET"
        }
      }
    }

    "when calling PUT on the /workspaces*/*/method_configs/copyFromMethodRepo path" - {
      "MethodNotAllowed error is returned" in {
        Put(validCopyFromRepoUrl) ~> sealRoute(routes) ~> check {
          status should equal(MethodNotAllowed)
          responseAs[String] === "HTTP method not allowed, supported methods: GET"
        }
      }
    }

  }

}
