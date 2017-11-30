package org.broadinstitute.dsde.firecloud.webservice

import org.broadinstitute.dsde.firecloud.service.{BaseServiceSpec, StatusService}
import org.broadinstitute.dsde.workbench.util.health.StatusJsonSupport.StatusCheckResponseFormat
import org.broadinstitute.dsde.workbench.util.health.Subsystems._
import org.broadinstitute.dsde.workbench.util.health.{HealthMonitor, StatusCheckResponse}
import spray.http.HttpMethods.GET
import spray.http.StatusCodes.OK
import spray.httpx.SprayJsonSupport._

import scala.concurrent.duration._


/*  We don't do much testing of the HealthMonitor itself, because that's tested as part of
    workbench-libs. Here, we test routing, de/serialization, and the config we send into
    the HealthMonitor.
 */
class StatusApiServiceSpec extends BaseServiceSpec with StatusApiService {

  def actorRefFactory = system

  val healthMonitorChecks = app.healthMonitorChecks
  val healthMonitor = system.actorOf(HealthMonitor.props(healthMonitorChecks.keySet)( () => healthMonitorChecks ), "health-monitor")
  val monitorSchedule = system.scheduler.schedule(Duration.Zero, 1.second, healthMonitor, HealthMonitor.CheckAll)

  override def beforeAll = {
    // wait for the healthMonitor to start up ...
    Thread.sleep(3000)
  }

  override def afterAll = {
    monitorSchedule.cancel
  }

  override val statusServiceConstructor: () => StatusService = StatusService.constructor(healthMonitor)

  val statusPath = "/status"

  "Status endpoint" - {
    allHttpMethodsExcept(GET) foreach { method =>
      s"should reject ${method.toString} method" in {
        new RequestBuilder(method)(statusPath) ~> statusRoutes ~> check {
          assert(!handled)
        }
      }
    }
    "should return OK for an unauthenticated GET" in {
      Get(statusPath) ~> statusRoutes ~> check {
        assert(status == OK)
      }
    }
    "should deserialize to a StatusCheckResponse" in {
      Get(statusPath) ~> statusRoutes ~> check {
        responseAs[StatusCheckResponse]
      }
    }
    "should contain all the subsystems we care about" in {
      Get(statusPath) ~> statusRoutes ~> check {
        val statusCheckResponse = responseAs[StatusCheckResponse]
        val expectedSystems = Set(Agora, Consent, GoogleBuckets, LibraryIndex, OntologyIndex, Rawls, Sam, Thurloe)
        assertResult(expectedSystems) { statusCheckResponse.systems.keySet }
      }
    }
  }

}