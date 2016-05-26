package scripts

import edu.eckerd.google.api.services.directory.Directory
import persistence.entities.tables.GOOGLE_USERS
import utils.configuration.ConfigurationModuleImpl
import utils.persistence.PersistenceModuleImpl

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by davenpcm on 5/26/16.
  */
object GoogleUpdateGoogleUsers {

  val modules = new ConfigurationModuleImpl with PersistenceModuleImpl
  import modules.dbConfig.driver.api._
  val db = modules.db
  val adminDir = Directory()

  val tableQuery = TableQuery[GOOGLE_USERS]

  def update() = {

    val users = adminDir.users.list("test.eckerd.edu") ::: adminDir.users.list()

    val existingUsers = Await.result(modules.db.run(tableQuery.result), Duration.Inf).toSet

    val newUsers = users.filterNot(existingUsers(_))
    val addUsers = tableQuery ++= newUsers

    val result = Await.result(modules.db.run(addUsers), Duration.Inf)
    result
  }

  def create() = {
    val action = tableQuery.schema.create
    Await.result(db.run(action), Duration.Inf)
  }

  def drop() = {
    val action = tableQuery.schema.drop
    Await.result(db.run(action), Duration.Inf)
  }


}
