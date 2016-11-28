package org.broadinstitute.dsde.firecloud

import org.broadinstitute.dsde.firecloud.model.ErrorReport
import spray.http.StatusCode

class FireCloudException(message: String = null, cause: Throwable = null) extends Exception(message, cause)

class FireCloudExceptionWithErrorReport(val errorReport: ErrorReport) extends FireCloudException(errorReport.toString)