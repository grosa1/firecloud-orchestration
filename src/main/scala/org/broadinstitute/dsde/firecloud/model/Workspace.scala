package org.broadinstitute.dsde.firecloud.model

import org.broadinstitute.dsde.firecloud.FireCloudConfig
import org.broadinstitute.dsde.firecloud.model.Attributable.AttributeMap

case class WorkspaceName(
  namespace: Option[String] = None,
  name: Option[String] = None)

case class WorkspaceEntity(
  namespace: Option[String] = None,
  name: Option[String] = None,
  createdDate: Option[String] = None,
  createdBy: Option[String] = None,
  attributes: Option[AttributeMap] = None)

case class WorkspaceCreate(
  namespace: String,
  name: String,
  attributes: AttributeMap,
  isProtected: Option[Boolean] = Some(false))

case class RawlsWorkspaceCreate(
  namespace: String,
  name: String,
  attributes: AttributeMap,
  realm: Option[Map[String, String]] = None) {
  def this(wc: WorkspaceCreate) =
    this(wc.namespace, wc.name, wc.attributes,
      if (wc.isProtected.getOrElse(false))
        Some(Map("realmName" -> FireCloudConfig.Nih.rawlsGroupName))
      else None)
}

case class RawlsWorkspaceResponse(
  accessLevel: String,
  canShare: Option[Boolean] = None,
  workspace: RawlsWorkspace,
  workspaceSubmissionStats: SubmissionStats,
  owners: List[String])

case class RawlsWorkspace(
  workspaceId: String,
  namespace: String,
  name: String,
  isLocked: Option[Boolean] = None,
  createdBy: String,
  createdDate: String,
  lastModified: Option[String] = None,
  attributes: AttributeMap,
  bucketName: String,
  accessLevels: Map[String, Map[String, String]],
  realm: Option[Map[String, String]])

case class RawlsEntity(name: String, entityType: String, attributes: AttributeMap)

case class SubmissionStats(
  lastSuccessDate: Option[String] = None,
  lastFailureDate: Option[String] = None,
  runningSubmissionsCount: Int)

case class UIWorkspaceResponse(
  accessLevel: Option[String] = None,
  canShare: Option[Boolean] = None,
  workspace: Option[UIWorkspace] = None,
  workspaceSubmissionStats: Option[SubmissionStats] = None,
  owners: Option[List[String]] = None) {
  def this(rwr: RawlsWorkspaceResponse) =
    this(Option(rwr.accessLevel), rwr.canShare, Option(new UIWorkspace(rwr.workspace)), Option(rwr.workspaceSubmissionStats), Option(rwr.owners))
}

case class UIWorkspace(
  workspaceId: String,
  namespace: String,
  name: String,
  isLocked: Option[Boolean] = None,
  createdBy: String,
  createdDate: String,
  lastModified: Option[String] = None,
  attributes: AttributeMap,
  bucketName: String,
  accessLevels: Map[String, Map[String, String]],
  realm: Option[Map[String, String]],
  isProtected: Boolean) {
  def this(rw: RawlsWorkspace) =
    this(rw.workspaceId, rw.namespace, rw.name, rw.isLocked, rw.createdBy, rw.createdDate,
      rw.lastModified, rw.attributes, rw.bucketName, rw.accessLevels, rw.realm,
      rw.realm.flatMap(_.get("realmName").map(_ == FireCloudConfig.Nih.rawlsGroupName)).getOrElse(false))
}

case class EntityCreateResult(entityType: String, entityName: String, succeeded: Boolean, message: String)

case class EntityCopyDefinition(
  sourceWorkspace: WorkspaceName,
  entityType: String,
  entityNames: Seq[String]
  )

case class EntityCopyWithDestinationDefinition(
  sourceWorkspace: WorkspaceName,
  destinationWorkspace: WorkspaceName,
  entityType: String,
  entityNames: Seq[String]
  )

case class EntityId(entityType: String, entityName: String)
case class EntityDeleteDefinition(recursive: Boolean, entities: Seq[EntityId])

case class MethodConfigurationId(
  name: Option[String] = None,
  namespace: Option[String] = None,
  workspaceName: Option[WorkspaceName] = None)

case class MethodConfigurationCopy(
  methodRepoNamespace: Option[String] = None,
  methodRepoName: Option[String] = None,
  methodRepoSnapshotId: Option[Int] = None,
  destination: Option[MethodConfigurationId] = None)

case class MethodConfigurationPublish(
  methodRepoNamespace: Option[String] = None,
  methodRepoName: Option[String] = None,
  source: Option[MethodConfigurationId] = None)

case class CopyConfigurationIngest(
  configurationNamespace: Option[String],
  configurationName: Option[String],
  configurationSnapshotId: Option[Int],
  destinationNamespace: Option[String],
  destinationName: Option[String])

case class PublishConfigurationIngest(
  configurationNamespace: Option[String],
  configurationName: Option[String],
  sourceNamespace: Option[String],
  sourceName: Option[String])

case class SubmissionIngest(
  methodConfigurationNamespace: Option[String],
  methodConfigurationName: Option[String],
  entityType: Option[String],
  entityName: Option[String],
  expression: Option[String])

case class RawlsGroupMemberList(
  userEmails: Option[Seq[String]] = None,
  subGroupEmails: Option[Seq[String]] = None,
  userSubjectIds: Option[Seq[String]] = None,
  subGroupNames: Option[Seq[String]] = None)

case class RawlsBucketUsageResponse(usageInBytes: BigInt)

case class WorkspaceStorageCostEstimate(estimate: String)
