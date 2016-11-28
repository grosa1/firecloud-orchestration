package org.broadinstitute.dsde.firecloud.webservice

import java.text.SimpleDateFormat

import akka.actor.{Actor, Props}
import org.broadinstitute.dsde.firecloud.model.Attributable.AttributeMap
import org.broadinstitute.dsde.firecloud.model._
import org.broadinstitute.dsde.firecloud.model.ModelJsonProtocol._
import org.broadinstitute.dsde.firecloud.model.WorkspaceACLJsonSupport._
import org.broadinstitute.dsde.firecloud.service.{FireCloudDirectives, FireCloudRequestBuilding, WorkspaceService}
import org.broadinstitute.dsde.firecloud.utils.StandardUserInfoDirectives
import org.broadinstitute.dsde.firecloud.{EntityClient, FireCloudConfig}
import org.slf4j.LoggerFactory
import spray.json._
import spray.json.DefaultJsonProtocol._
import spray.http.{HttpEntity, HttpMethods, OAuth2BearerToken}
import spray.httpx.unmarshalling._
import spray.httpx.SprayJsonSupport._
import spray.routing._

import scala.concurrent.ExecutionContext

trait WorkspaceApiService extends HttpService with FireCloudRequestBuilding
  with FireCloudDirectives with StandardUserInfoDirectives {

  private implicit val ec: ExecutionContext = actorRefFactory.dispatcher

  private final val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  lazy val log = LoggerFactory.getLogger(getClass)
  lazy val rawlsWorkspacesRoot = FireCloudConfig.Rawls.workspacesUrl

  val workspaceServiceConstructor: WithAccessToken => WorkspaceService

  def transformSingleWorkspaceRequest(entity: HttpEntity): HttpEntity = {
    entity.as[RawlsWorkspaceResponse] match {
      case Right(rwr) => new UIWorkspaceResponse(rwr).toJson.prettyPrint
      case Left(error) =>
        log.error("Unable to unmarshal entity -- " + error.toString)
        entity
    }
  }

  def transformListWorkspaceRequest(entity: HttpEntity): HttpEntity = {
    entity.as[List[RawlsWorkspaceResponse]] match {
      case Right(lrwr) => lrwr.map(new UIWorkspaceResponse(_)).toJson.prettyPrint
      case Left(error) =>
        log.error("Unable to unmarshal entity -- " + error.toString)
        entity
    }
  }

  private val filename = "-workspace-attributes.tsv"

  val workspaceRoutes: Route =
    pathPrefix("cookie-authed") {
      path("workspaces" / Segment / Segment / "exportAttributesTSV") {
          (workspaceNamespace, workspaceName) =>
            cookie("FCtoken") { tokenCookie =>
                mapRequest(r => addCredentials(OAuth2BearerToken(tokenCookie.content)).apply(r)) { requestContext =>
                    perRequest(requestContext,
                        WorkspaceService.props(workspaceServiceConstructor, new AccessToken(OAuth2BearerToken(tokenCookie.content))),
                        WorkspaceService.ExportWorkspaceAttributesTSV(workspaceNamespace, workspaceName, workspaceName + filename))
                  }
              }
        }
    } ~
    pathPrefix("api") {
      pathPrefix("workspaces") {
        pathEnd {
          requireUserInfo() { _ =>
            mapHttpResponseEntity(transformListWorkspaceRequest) {
              passthrough(rawlsWorkspacesRoot, HttpMethods.GET)
            }
          } ~
          post {
            requireUserInfo() { _ =>
              entity(as[WorkspaceCreate]) { createRequest => requestContext =>
                val extReq = Post(FireCloudConfig.Rawls.workspacesUrl, new RawlsWorkspaceCreate(createRequest))
                externalHttpPerRequest(requestContext, extReq)
              }
            }
          }
        } ~
        pathPrefix(Segment / Segment) { (workspaceNamespace, workspaceName) =>
          val workspacePath = rawlsWorkspacesRoot + "/%s/%s".format(workspaceNamespace, workspaceName)
          pathEnd {
            requireUserInfo() { _ =>
              mapHttpResponseEntity(transformSingleWorkspaceRequest) {
                passthrough(workspacePath, HttpMethods.GET)
              } ~
                passthrough(workspacePath, HttpMethods.DELETE)
            }
          } ~
          path("methodconfigs") {
            requireUserInfo() { _ =>
              passthrough(workspacePath + "/methodconfigs", HttpMethods.GET, HttpMethods.POST)
            }
          } ~
          path("importEntities") {
            post {
              requireUserInfo() { _ =>
                formFields('entities) { entitiesTSV =>
                  respondWithJSON { requestContext =>
                    perRequest(requestContext, Props(new EntityClient(requestContext)),
                      EntityClient.ImportEntitiesFromTSV(workspaceNamespace, workspaceName, entitiesTSV))
                  }
                }
              }
            }
          } ~
          path("updateAttributes") {
            requireUserInfo() { _ =>
              passthrough(workspacePath, HttpMethods.PATCH)
            }
          } ~
          path("setAttributes") {
            patch {
              requireUserInfo() { userInfo =>
                implicit val impAttributeFormat: AttributeFormat = new AttributeFormat with PlainArrayAttributeListSerializer
                entity(as[AttributeMap]) { newAttributes => requestContext =>
                  perRequest(requestContext,
                    WorkspaceService.props(workspaceServiceConstructor, userInfo),
                    WorkspaceService.SetWorkspaceAttributes(workspaceNamespace, workspaceName, newAttributes))
                }
              }
            }
          } ~
          path("exportAttributesTSV") {
            get {
              requireUserInfo() { userInfo => requestContext =>
                perRequest(requestContext,
                  WorkspaceService.props(workspaceServiceConstructor, userInfo),
                  WorkspaceService.ExportWorkspaceAttributesTSV(workspaceNamespace, workspaceName, workspaceName + filename))
              }
            }
          } ~
          path("importAttributesTSV") {
            post {
              requireUserInfo() { userInfo =>
                formFields('attributes) { attributesTSV =>
                  respondWithJSON { requestContext =>
                    perRequest(requestContext, WorkspaceService.props(workspaceServiceConstructor, userInfo),
                      WorkspaceService.ImportAttributesFromTSV(workspaceNamespace, workspaceName, attributesTSV)
                    )
                  }
                }
              }
            }
          } ~
          path("acl") {
            patch {
              requireUserInfo() { userInfo =>
                entity(as[List[WorkspaceACLUpdate]]) { aclUpdates => requestContext =>
                  perRequest(requestContext,
                    WorkspaceService.props(workspaceServiceConstructor, userInfo),
                    WorkspaceService.UpdateWorkspaceACL(workspaceNamespace, workspaceName, aclUpdates, userInfo.userEmail))
                }
              }
            } ~
            get {
              requireUserInfo() { _ =>
                passthrough(workspacePath + "/acl", HttpMethods.GET)
              }
            }
          } ~
          path("checkBucketReadAccess") {
            requireUserInfo() { _ =>
              passthrough(workspacePath + "/checkBucketReadAccess", HttpMethods.GET)
            }
          } ~
          path("clone") {
            post {
              requireUserInfo() { _ =>
                entity(as[WorkspaceCreate]) { createRequest => requestContext =>
                  val extReq = Post(workspacePath + "/clone", new RawlsWorkspaceCreate(createRequest))
                  externalHttpPerRequest(requestContext, extReq)
                }
              }
            }
          } ~
          path("lock") {
            requireUserInfo() { _ =>
              passthrough(workspacePath + "/lock", HttpMethods.PUT)
            }
          } ~
          path("unlock") {
            requireUserInfo() { _ =>
              passthrough(workspacePath + "/unlock", HttpMethods.PUT)
            }
          }
        }
      }
    }
}