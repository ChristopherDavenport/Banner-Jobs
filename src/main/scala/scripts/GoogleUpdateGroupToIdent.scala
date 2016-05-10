package scripts


import google.services.admin.directory.models.Member
import utils.configuration.ConfigurationModuleImpl
import utils.persistence.PersistenceModuleImpl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import google.services.admin.directory.Directory
import persistence.entities.representations.Group2Ident_R
import persistence.entities.tables.GROUPTOIDENT

import scala.util.Try

/**
  * Created by davenpcm on 4/21/2016.
  */
object GoogleUpdateGroupToIdent {

  def update(implicit service: Directory) = {
    val modules = new ConfigurationModuleImpl with PersistenceModuleImpl
    import modules.dbConfig.driver.api._
    val db = modules.db

    case class GroupIdent(id: String, name: String, email: String, count: Long, desc: String)


    val group2IdentTableQuery = TableQuery[GROUPTOIDENT]

    // Create Table or Silently Fail
//    Await.result(db.run(group2IdentTableQuery.schema.create), Duration.Inf)

    val currentGroups = service.groups.list()

    val groupidents = currentGroups.map(group =>
      GroupIdent(group.id.get, group.name, group.email, group.directMemberCount.get, group.description.get)
    )

    val group2Members = groupidents.map { ident =>
      service.members.list(ident.email)
        .map(member =>
          (Group2Ident_R(ident.id, member.id.get, "", member.role, member.memberType),
            Await.result(db.run(group2IdentTableQuery.withFilter(rec =>
              rec.groupId === ident.id && rec.identID === member.id.get).result.headOption), Duration(1, "second"))))
    }

    val Tuples = group2Members
      .foldLeft(List[(Group2Ident_R, Option[Group2Ident_R])]())((acc, next) => next ::: acc)

    val idents = Tuples.map { tuple =>
      val ident = Group2Ident_R(
        tuple._1.groupId,
        tuple._1.identID,
        tuple._2 match {
          case None => "N"
          case Some(rec) => rec.autoIndicator
        },
        tuple._1.memberRole,
        tuple._1.memberType,
        tuple._2.flatMap(rec => rec.processIndicator)
      )
      (ident, tuple._2)
    }

    val UpdatingFuture = Future.sequence(
      idents map{ ident =>
        ident._2 match {
          case Some(rec) => Future[Unit]()
          case None => db.run(group2IdentTableQuery += ident._1)
        }
      }
    )

    Await.result(UpdatingFuture, Duration.Inf)
  }

}
