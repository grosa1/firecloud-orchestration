package org.broadinstitute.dsde.firecloud.webservice

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.broadinstitute.dsde.firecloud.Application
import org.broadinstitute.dsde.firecloud.dataaccess._
import org.broadinstitute.dsde.firecloud.mock.MockGoogleServicesDAO
import org.broadinstitute.dsde.firecloud.service.NihService
import org.broadinstitute.dsde.firecloud.utils.TestRequestBuilding
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

/**
  * Created by mbemis on 3/1/17.
  */

// common trait to be inherited by API service tests
trait ApiServiceSpec extends AnyFlatSpec with Matchers with ScalatestRouteTest with SprayJsonSupport with TestRequestBuilding {
  // increase the timeout for ScalatestRouteTest from the default of 1 second, otherwise
  // intermittent failures occur on requests not completing in time
  implicit val routeTestTimeout = RouteTestTimeout(5.seconds)

  def actorRefFactory = system

  trait ApiServices extends NihApiService {
    val agoraDao: MockAgoraDAO
    val googleDao: MockGoogleServicesDAO
    val ontologyDao: MockOntologyDAO
    val consentDao: MockConsentDAO
    val rawlsDao: MockRawlsDAO
    val samDao: MockSamDAO
    val searchDao: MockSearchDAO
    val researchPurposeSupport: MockResearchPurposeSupport
    val thurloeDao: MockThurloeDAO
    val shareLogDao: MockShareLogDAO
    val importServiceDao: ImportServiceDAO
    val shibbolethDao: ShibbolethDAO

    def actorRefFactory = system

    val nihServiceConstructor = NihService.constructor(
      new Application(agoraDao, googleDao, ontologyDao, consentDao, rawlsDao, samDao, searchDao, researchPurposeSupport, thurloeDao, shareLogDao, importServiceDao, shibbolethDao)
    ) _

  }

  // lifted from rawls. prefer this to using theSameElementsAs directly, because its functionality depends on whitespace
  def assertSameElements[T](expected: IterableOnce[T], actual: IterableOnce[T]): Unit = {
    expected.iterator.to(Iterable) should contain theSameElementsAs actual.iterator.to(Iterable)
  }

}
