package org.broadinstitute.dsde.firecloud

import org.parboiled.common.FileUtils
import org.slf4j.LoggerFactory
import spray.http.StatusCodes._
import spray.http.Uri.Path
import spray.http._
import spray.routing.{HttpServiceActor, Route}
import spray.util._

import org.broadinstitute.dsde.firecloud.service._

class FireCloudServiceActor extends HttpServiceActor {
  implicit val system = context.system

  trait ActorRefFactoryContext {
    def actorRefFactory = context
  }

  val methodsService = new MethodsService with ActorRefFactoryContext
  val workspaceService = new WorkspaceService with ActorRefFactoryContext
  val entityService = new EntityService with ActorRefFactoryContext
  val methodConfigurationService = new MethodConfigurationService with ActorRefFactoryContext
  val submissionsService = new SubmissionService with ActorRefFactoryContext
  val routes = methodsService.routes ~ workspaceService.routes ~ entityService.routes ~
    methodConfigurationService.routes ~ submissionsService.routes

  lazy val log = LoggerFactory.getLogger(getClass)
  val logRequests = mapInnerRoute { route => requestContext =>
    log.debug(requestContext.request.toString)
    route(requestContext)
  }

  def receive = runRoute(
    logRequests {
      swaggerUiService ~ routes ~
        // The "api" path prefix is never visible to this server in production because it is
        // transparently proxied. We check for it here so the redirects work when visiting this
        // server directly during local development.
        pathPrefix("api") {
          swaggerUiService ~ routes
        }
    }
  )

  private val uri = extract { c => c.request.uri }
  private val swaggerUiPath = "META-INF/resources/webjars/swagger-ui/2.1.1"

  val swaggerUiService = {
    get {
      pathPrefix("") { pathEnd{ uri { uri =>
        redirectToSwagger(uri.withPath(uri.path + "api/swagger/"))
      } } } ~
      pathPrefix("swagger") {
        pathEnd { uri { uri => redirectToSwagger(uri.withPath(Path("/api") ++ uri.path + "/")) } } ~
        pathSingleSlash { uri { uri =>
          redirectToSwagger(uri.withPath(Path("/api") ++ uri.path))
        } } ~
          serveYaml("swagger") ~ getFromResourceDirectory(swaggerUiPath)
      }
    }
  }

  private def redirectToSwagger(baseUri: Uri): Route = {
    redirect(
      baseUri.withPath(baseUri.path + "index.html").withQuery(
        ("url", baseUri.path.toString + "api-docs")
      ),
      TemporaryRedirect
    )
  }

  private def serveYaml(resourceDirectoryBase: String): Route = {
    unmatchedPath { path =>
      extract(_.request.uri) { uri =>
        val classLoader = actorSystem(actorRefFactory).dynamicAccess.classLoader
        classLoader.getResource(resourceDirectoryBase + path + ".yaml") match {
          case null => reject
          case url => complete {
            val inputStream = url.openStream()
            try {
              val yaml = FileUtils.readAllText(inputStream)
              val port = uri.authority.port
              val portSuffix = if (port != 80 && port != 443) ":" + port else ""
              val host = uri.authority.host + portSuffix
              yaml.format(host)
            } finally {
              inputStream.close()
            }
          }
        }
      }
    }
  }
}

