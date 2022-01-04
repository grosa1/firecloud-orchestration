package org.broadinstitute.dsde.firecloud.service

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.dataaccess._
import org.broadinstitute.dsde.firecloud.model.ModelJsonProtocol._
import org.broadinstitute.dsde.firecloud.model._
import org.broadinstitute.dsde.firecloud.service.PerRequest.{PerRequestMessage, RequestComplete}
import org.broadinstitute.dsde.firecloud.{Application, FireCloudConfig, FireCloudExceptionWithErrorReport}
import org.broadinstitute.dsde.rawls.model.Notifications.{ActivationNotification, NotificationFormat}
import org.broadinstitute.dsde.rawls.model.{ErrorReport, RawlsUserSubjectId}
import akka.http.scaladsl.model.StatusCodes
import org.broadinstitute.dsde.firecloud.FireCloudConfig.Sam
import org.broadinstitute.dsde.workbench.util.Retry

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object RegisterService {
  val samTosTextUrl = s"${Sam.baseUrl}/tos/text"

  def constructor(app: Application)()(implicit executionContext: ExecutionContext) =
    new RegisterService(app.rawlsDAO, app.samDAO, app.thurloeDAO, app.googleServicesDAO, app.system)

}

class RegisterService(val rawlsDao: RawlsDAO, val samDao: SamDAO, val thurloeDao: ThurloeDAO, val googleServicesDAO: GoogleServicesDAO, val system: ActorSystem)
  (implicit protected val executionContext: ExecutionContext) extends Retry with LazyLogging {

  def createUpdateProfile(userInfo: UserInfo, basicProfile: BasicProfile): Future[PerRequestMessage] = {
    for {
      _ <- thurloeDao.saveProfile(userInfo, basicProfile)
      _ <- thurloeDao.saveKeyValues(userInfo, Map("isRegistrationComplete" -> Profile.currentVersion.toString))
      isRegistered <- isRegistered(userInfo)
      userStatus <- if (!isRegistered.enabled.google || !isRegistered.enabled.ldap) {
        for {
          _ <- thurloeDao.saveKeyValues(userInfo,  Map("email" -> userInfo.userEmail))
         // _ <- Future(logger.info(s"XXX registering email ${userInfo.userEmail} with Sam after 10 seconds"))
         // _ <- Future(Thread.sleep(10000))
          registerResult <- registerUser(userInfo, basicProfile.termsOfService)
        } yield registerResult
      } else {
        Future.successful(isRegistered)
      }
    } yield {
      RequestComplete(StatusCodes.OK, userStatus)
    }
  }

  private def isRegistered(userInfo: UserInfo): Future[RegistrationInfo] = {
    samDao.getRegistrationStatus(userInfo) recover {
      case e: FireCloudExceptionWithErrorReport if e.errorReport.statusCode == Option(StatusCodes.NotFound) =>
        RegistrationInfo(WorkbenchUserInfo(userInfo.id, userInfo.userEmail), WorkbenchEnabled(false, false, false))
    }
  }

  private def registerUser(userInfo: UserInfo, termsOfService: Option[String]): Future[RegistrationInfo] = {
    for {
      registrationInfo <- (retryUntilSuccessOrTimeout(failureLogMessage = "User registration failed")(1.second, 10.seconds)(() => samDao.registerUser(termsOfService)(userInfo))): Future[RegistrationInfo]
      _ <- googleServicesDAO.publishMessages(FireCloudConfig.Notification.fullyQualifiedNotificationTopic, Seq(NotificationFormat.write(ActivationNotification(RawlsUserSubjectId(userInfo.id))).compactPrint))
    } yield {
      registrationInfo
    }
  }

  /*  utility method to determine if a preferences key headed to Thurloe is valid for user input.
      Add whitelist logic here if you need to allow more keys.
      If we find we update this logic frequently, I suggest moving the predicates to a config file!
   */
  private def isValidPreferenceKey(key: String): Boolean = {
    key.startsWith("notifications/")
  }

  def updateProfilePreferences(userInfo: UserInfo, preferences: Map[String, String]): Future[PerRequestMessage] = {
    if (preferences.keys.forall(isValidPreferenceKey)) {
      thurloeDao.saveKeyValues(userInfo, preferences).map(_ => RequestComplete(StatusCodes.NoContent))
    } else {
      throw new FireCloudExceptionWithErrorReport(ErrorReport(StatusCodes.BadRequest, "illegal preference key"))
    }
  }
}
