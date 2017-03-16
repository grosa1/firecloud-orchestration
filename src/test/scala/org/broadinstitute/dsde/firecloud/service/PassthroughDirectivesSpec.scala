package org.broadinstitute.dsde.firecloud.service

import org.broadinstitute.dsde.firecloud.service.PassthroughDirectivesSpec._
import org.broadinstitute.dsde.firecloud.service.PassthroughDirectivesSpecSupport._
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpCallback.callback
import org.mockserver.model.HttpRequest.request
import spray.http.HttpMethods
import spray.http.HttpMethods._
import spray.http.StatusCodes._
import spray.httpx.SprayJsonSupport
import spray.routing.HttpServiceBase



object PassthroughDirectivesSpec {
  val echoPort = 9123
  val echoUrl = s"http://localhost:$echoPort"
}

class PassthroughDirectivesSpec extends BaseServiceSpec with HttpServiceBase
  with FireCloudDirectives with SprayJsonSupport {

  def actorRefFactory = system

  var echoServer: ClientAndServer = _

  override def beforeAll = {
    echoServer = startClientAndServer(echoPort)
      echoServer.when(request())
        .callback(
          callback().
            withCallbackClass("org.broadinstitute.dsde.firecloud.service.EchoCallback"))
  }

  override def afterAll = {
    echoServer.stop
  }


  "Passthrough Directives" - {
    "passthrough() directive" - {
      "root path '/'" - {
        "should hit the root path '/'" in {
          validateUri("/")
        }
      }
      "path with multiple segments" - {
        "should hit the path exactly" in {
          validateUri("/one/2/three")
        }
      }
      "path with a hex-encoded segment" - {
        "should hit the path exactly" in {
          validateUri(
            "/one/%32/three",
            "/one/2/three"
          )
        }
      }
      "path with a single query parameter" - {
        "should send the query parameter through" in {
          validateUri("/one/2/three?key=value", Some(Map("key"->"value")))
        }
      }
      "path with multiple query parameters" - {
        "should send the query parameters through" in {
          validateUri("/one/2/three?key=value&key2=val2", Some(Map("key"->"value", "key2"->"val2")))
        }
      }
      "path with encoded query parameters" - {
        "should send the query parameters through" in {
          validateUri(
            "/one/2/three?key=value&key2=1%323",
            "/one/2/three?key=value&key2=123",
            Some(Map("key"->"value", "key2"->"123"))
          )
        }
      }
      "all http methods" - {
        Seq(CONNECT, DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT, TRACE) foreach { meth =>
          s"$meth should pass through correctly" in {
            val specRoute = passthrough(echoUrl + "/", meth)
            val reqMethod = new RequestBuilder(meth)
            reqMethod() ~> sealRoute(specRoute) ~> check {
              assertResult(OK) {status}
              // special handling for HEAD, because HEAD won't return a body
              if (meth != HEAD) {
                val info = responseAs[RequestInfo]
                assertResult(echoUrl + "/") {
                  info.url
                }
                assertResult(meth.value, s"testing http method ${meth.value}") {
                  info.method
                }
              }
            }
          }
        }
      }
    }

    "passthroughAllPaths() directive" - {
      // passthroughAllPaths requires the "our" root to have a value
      val pathPairs = Seq(
        ("ours",""),
        ("ours","theirs"),
        ("ours/twice","theirs"),
        ("ours/twice","theirs/twice"),
        ("ours","theirs/twice")
      )
      pathPairs foreach { dirPairs: (String, String) =>
        s"using directories [${dirPairs._1}] and [${dirPairs._2}]" - {
          implicit val ourTheirRoots:(String,String) = dirPairs

          "root path '/'" - {
            "should hit the root path '/'" in {
              validateAllPaths("/")
            }
          }
          "path with multiple segments" - {
            "should hit the path exactly" in {
              validateAllPaths("/one/2/three")
            }
          }
          "path with a hex-encoded segment" - {
            "should hit the path exactly" in {
              validateAllPaths(
                "/one/%32/three",
                "/one/2/three"
              )
            }
          }
          "path with a single query parameter" - {
            "should send the query parameter through" in {
              validateAllPaths("/one/2/three?key=value", Some(Map("key" -> "value")))
            }
          }
          "path with multiple query parameters" - {
            "should send the query parameters through" in {
              validateAllPaths("/one/2/three?key=value&key2=val2", Some(Map("key" -> "value", "key2" -> "val2")))
            }
          }
          "path with encoded query parameters" - {
            "should send the query parameters through" in {
              validateAllPaths(
                "/one/2/three?key=value&key2=1%323",
                "/one/2/three?key=value&key2=123",
                Some(Map("key" -> "value", "key2" -> "123"))
              )
            }
          }
        }
      }
    }
  }

  private def validateUri(path:String, queryParams:Option[Map[String,String]]=None): Unit = {
    validateUri(path, path, queryParams)
  }

  private def validateUri(inpath:String, outpath:String): Unit = {
    validateUri(inpath, outpath, None)
  }

  private def validateUri(inpath:String, outpath:String, queryParams:Option[Map[String,String]]): Unit = {
    val specRoute = passthrough(echoUrl + inpath, HttpMethods.GET)
    Get() ~> sealRoute(specRoute) ~> check {
      val info = responseAs[RequestInfo]
      assertResult(echoUrl + outpath) {
        info.url
      }
      if (queryParams.isDefined) {
        assertResult(queryParams.get) {
          info.queryparams
        }
      }
    }
  }

  private def validateAllPaths(path:String, queryParams:Option[Map[String,String]]=None)(implicit ourTheirRoots:(String,String)): Unit = {
    validateAllPaths(path, path, queryParams)
  }

  private def validateAllPaths(inpath:String, outpath:String)(implicit ourTheirRoots:(String,String)): Unit = {
    validateAllPaths(inpath, outpath, None)
  }

  private def validateAllPaths(inpath:String, outpath:String, queryParams:Option[Map[String,String]])
                              (implicit ourTheirRoots:(String,String)): Unit = {
    validateAllPathsWithRoots(inpath,outpath,queryParams,ourTheirRoots._1,ourTheirRoots._2)
  }

  private def validateAllPathsWithRoots(inpath:String, outpath:String, queryParams:Option[Map[String,String]],
                                       inroot:String, outroot:String): Unit = {
    val actualInRoot =    if (inroot.equals("")) "" else "/"+inroot
    val expectedOutRoot = if (outroot.equals("")) "" else "/"+outroot
    val specRoute = passthroughAllPaths(inroot, echoUrl + expectedOutRoot)

    Get(actualInRoot + inpath) ~> sealRoute(specRoute) ~> check {
      val info = responseAs[RequestInfo]
      assertResult(echoUrl + expectedOutRoot + outpath) {
        info.url
      }
      if (queryParams.isDefined) {
        assertResult(queryParams.get) {
          info.queryparams
        }
      }
    }
  }

}