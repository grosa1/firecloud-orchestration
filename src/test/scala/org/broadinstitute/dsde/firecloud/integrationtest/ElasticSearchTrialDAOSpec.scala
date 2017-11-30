package org.broadinstitute.dsde.firecloud.integrationtest

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.FireCloudException
import org.broadinstitute.dsde.firecloud.dataaccess.ElasticSearchTrialDAO
import org.broadinstitute.dsde.firecloud.integrationtest.ESIntegrationSupport.{searchDAO, trialDAO}
import org.broadinstitute.dsde.firecloud.model.WorkbenchUserInfo
import org.broadinstitute.dsde.firecloud.model.Trial.TrialProject
import org.broadinstitute.dsde.rawls.model.RawlsBillingProjectName
import org.scalatest.{BeforeAndAfterAll, FreeSpec, Matchers}

class ElasticSearchTrialDAOSpec extends FreeSpec with Matchers with BeforeAndAfterAll with LazyLogging  {

  override def beforeAll = {
    // using the delete from search dao, because we don't have recreate in trial dao.
    searchDAO.recreateIndex()

    // set up example data
    logger.info("indexing fixtures ...")
    ElasticSearchTrialDAOFixtures.fixtureProjects foreach { project => trialDAO.insertProjectRecord(project.name)}

    ElasticSearchTrialDAOFixtures.fixtureProjects collect {
      case project if project.verified => trialDAO.setProjectRecordVerified(project.name, verified=project.verified)
    }
    ElasticSearchTrialDAOFixtures.fixtureProjects collect {
      case project if project.user.nonEmpty => trialDAO.claimProjectRecord(project.user.get)
    }
    logger.info("... fixtures indexed.")
  }

  override def afterAll = {
    // using the delete from search dao, because we don't have recreate in trial dao.
    searchDAO.deleteIndex()
  }

  "ElasticSearchTrialDAO" - {
    "createProject" - {
      "should insert a new project" in {
        val name = RawlsBillingProjectName("garlic")
        val actual = trialDAO.insertProjectRecord(name)
        val expected = TrialProject(name, verified=false, user=None)
        assertResult(expected) { actual }
        val expectedCheck = trialDAO.getProjectRecord(name)
        assertResult(expected) { expectedCheck }
      }
      "should throw error when inserting an existing project" in {
        val ex = intercept[FireCloudException] {
          trialDAO.insertProjectRecord(RawlsBillingProjectName("endive"))
        }
        assert(ex.getMessage == "ElasticSearch request failed")
        assert(ex.getCause.getMessage == "[billingproject][endive]: version conflict, document already exists (current version [1])")
      }
    }
    "verifyProject" - {
      "should update a project with a new value for verified" in {
        val name=RawlsBillingProjectName("endive")
        trialDAO.setProjectRecordVerified(name, verified=true)
        val actual1 = trialDAO.getProjectRecord(name)
        val expected1 = TrialProject(name, verified=true, user=None)
        assertResult(expected1) { actual1 }
        trialDAO.setProjectRecordVerified(name, verified=false)
        val actual2 = trialDAO.getProjectRecord(name)
        val expected2 = TrialProject(name, verified=false, user=None)
        assertResult(expected2) { actual2 }

      }
      "should throw an error if project is not found" in {
        val ex = intercept[FireCloudException] {
          trialDAO.setProjectRecordVerified(RawlsBillingProjectName("habanero"), verified=true)
        }
        assert(ex.getMessage == "project habanero not found!")
      }

    }
    "claimProject" - {
      "should claim the first available project by alphabetical order" in {
        val user = WorkbenchUserInfo("789", "me")
        val claimed = trialDAO.claimProjectRecord(user)
        val expected = TrialProject(RawlsBillingProjectName("date"), verified=true, user=Some(user))
        assertResult(expected) { claimed }
        val claimCheck = trialDAO.getProjectRecord(RawlsBillingProjectName("date"))
        assertResult(expected) { claimCheck }
      }
      "should throw an error when no available/verified projects exist" in {
        // this one should succeed - "fennel" is available
        val user = WorkbenchUserInfo("101010", "me2")
        val claimed = trialDAO.claimProjectRecord(user)
        // this one should fail - nothing left
        val ex = intercept[FireCloudException] {
          trialDAO.claimProjectRecord(user)
        }
        assert(ex.getMessage == "no available projects")
      }
    }
    "countAvailableProjects" - {
      "should return zero when no projects available" in {
        assertResult(0) { trialDAO.countAvailableProjects }
      }
      "should return accurate count of available projects" in {
        // insert three
        Seq("orange", "pineapple", "quince") foreach { proj => trialDAO.insertProjectRecord(RawlsBillingProjectName(proj))}
        // verify two
        Seq("orange", "quince") foreach { proj => trialDAO.setProjectRecordVerified(RawlsBillingProjectName(proj), verified=true)}
        assertResult(2) { trialDAO.countAvailableProjects }
      }
    }
    "projectReport" - {
      "should return an accurate report with only verified/claimed projects" in {
        // unverified/unclaimed projects listed below but commented out for developer clarity
        val expected = Seq(
          // TrialProject(RawlsBillingProjectName("apple"), verified=false, None),
          TrialProject(RawlsBillingProjectName("banana"), verified=true, Some(WorkbenchUserInfo("123", "alice@example.com"))),
          TrialProject(RawlsBillingProjectName("carrot"), verified=true, Some(WorkbenchUserInfo("456", "bob@example.com"))),
          TrialProject(RawlsBillingProjectName("date"), verified=true, Some(WorkbenchUserInfo("789", "me"))),
          // TrialProject(RawlsBillingProjectName("endive"), verified=false, None),
          TrialProject(RawlsBillingProjectName("fennel"), verified=true, Some(WorkbenchUserInfo("101010", "me2")))
          // TrialProject(RawlsBillingProjectName("garlic"), verified=false, None),
          // TrialProject(RawlsBillingProjectName("orange"), verified=true, None),
          // TrialProject(RawlsBillingProjectName("pineapple"), verified=false, None),
          // TrialProject(RawlsBillingProjectName("quince"), verified=true, None)
        )
        val actual = trialDAO.projectReport
        assertResult(expected) { actual }
      }
    }
    "concurrent updates" - {
      "should be rejected via Elasticsearch's versioning checks" in {
        // explicitly cast the dao to ElasticSearchTrialDAO (naughty!) so we can get access to
        // certain methods for testing
        val esTrialDAO = trialDAO.asInstanceOf[ElasticSearchTrialDAO]

        val name = RawlsBillingProjectName("jackfruit")

        // create the project
        trialDAO.insertProjectRecord(name)

        // get the project using internal
        val (version, project) = esTrialDAO.getProjectInternal(name)
        assert(version == 1)
        assert(project.name == name)

        // verify the project - this will increment the version in ES from 1 to 2
        trialDAO.setProjectRecordVerified(name, verified=true)
        val (newVersion, newProject) = esTrialDAO.getProjectInternal(name)
        assert(newVersion == 2)
        assert(newProject.name == name)

        // update the project using internal, specifying version 0. This should throw an error.
        val ex1 = intercept[FireCloudException] {
          esTrialDAO.updateProjectInternal(project, 1)
        }
        assert(ex1.getMessage == "ElasticSearch request failed")
        assert(ex1.getCause.getMessage == "[billingproject][jackfruit]: version conflict, current version [2] is different than the one provided [1]")

        // update the project using internal, specifying version 3. This should throw an error.
        val ex3 = intercept[FireCloudException] {
          esTrialDAO.updateProjectInternal(project, 3)
        }
        assert(ex3.getMessage == "ElasticSearch request failed")
        assert(ex3.getCause.getMessage == "[billingproject][jackfruit]: version conflict, current version [2] is different than the one provided [3]")
      }
    }
  }
}

object ElasticSearchTrialDAOFixtures {
  val fixtureProjects: Seq[TrialProject] = Seq(
    TrialProject(RawlsBillingProjectName("apple"), verified=false, None),
    TrialProject(RawlsBillingProjectName("banana"), verified=true, Some(WorkbenchUserInfo("123", "alice@example.com"))),
    TrialProject(RawlsBillingProjectName("carrot"), verified=true, Some(WorkbenchUserInfo("456", "bob@example.com"))),
    TrialProject(RawlsBillingProjectName("date"), verified=true, None),
    TrialProject(RawlsBillingProjectName("endive"), verified=false, None),
    TrialProject(RawlsBillingProjectName("fennel"), verified=true, None)
  )
}

