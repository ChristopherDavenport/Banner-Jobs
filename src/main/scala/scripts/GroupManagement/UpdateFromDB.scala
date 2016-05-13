package scripts.GroupManagement

import edu.eckerd.google.api.services.directory.Directory
import edu.eckerd.google.api.services.directory.models.Group
import persistence.entities.tables.GROUP_MASTER
import persistence.entities.representations.GroupMaster_R
import utils.configuration.ConfigurationModuleImpl
import utils.persistence.PersistenceModuleImpl
import language.higherKinds
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by davenpcm on 4/29/16.
  */
object UpdateFromDB extends App{
  val action = "debug"
  val term = "201530"

  val modules = new ConfigurationModuleImpl with PersistenceModuleImpl
  implicit val db = modules.db
  import modules.dbConfig.driver.api._
  val GROUP_MASTER_TABLEQUERY = TableQuery[GROUP_MASTER]

  implicit val adminDirectory = Directory()

  case class ClassGroupMember (courseName: String,
                              courseEmail: String,
                              studentAccountId: String,
                              studentEmail: String,
                              professorAccountId: String,
                              professorEmail: String)


  val data = sql"""SELECT
                    --     SSBSECT_CRN as CRN,
                    --     SSBSECT_SEQ_NUMB as SEQNO,
                    --     SSBSECT_SUBJ_CODE as SUBJECT,
                    --     SSBSECT_CRSE_NUMB as COURSE_NUMBER,
                    --     substr(SSBSECT_SEQ_NUMB, -1),
                    --     decode(substr(SFRSTCR_TERM_CODE, -2, 1), 1, 'fa', 2, 'sp', 3, 'su') as TERM_ALIAS,
                        nvl(SSBSECT_CRSE_TITLE, x.SCBCRSE_TITLE) as COURSE_TITLE,
                        lower(SSBSECT_SUBJ_CODE) || SSBSECT_CRSE_NUMB || '-' || substr(SSBSECT_SEQ_NUMB, -1) || '-' || decode(substr(SFRSTCR_TERM_CODE, -2, 1), 1, 'fa', 2, 'sp', 3, 'su') || '@eckerd.edu' as alias,
                        student.USERNAME as STUDENT_ACCOUNT,
                        student.EMAIL as STUDENT_EMAIL,
                        professor.USERNAME as PROFESSOR_ACCOUNT,
                        professor.EMAIL as PROFESSOR_EMAIL
                    FROM
                    SFRSTCR
                    INNER JOIN
                        SSBSECT
                            INNER JOIN SCBCRSE x
                                ON SCBCRSE_CRSE_NUMB = SSBSECT_CRSE_NUMB
                                AND SCBCRSE_SUBJ_CODE = SSBSECT_SUBJ_CODE
                            ON SSBSECT_TERM_CODE = SFRSTCR_TERM_CODE
                            AND SSBSECT_CRN = SFRSTCR_CRN
                            AND SSBSECT_ENRL > 0
                    LEFT JOIN SIRASGN
                            ON SIRASGN_TERM_CODE = SFRSTCR_TERM_CODE
                            AND SIRASGN_CRN = SFRSTCR_CRN
                            AND SIRASGN_PRIMARY_IND = 'Y'
                            AND SIRASGN_PIDM is not NULL
                    INNER JOIN STVRSTS
                            ON STVRSTS_CODE = SFRSTCR_RSTS_CODE
                            AND STVRSTS_INCL_ASSESS = 'Y'
                    INNER JOIN IDENT_MASTER student
                        ON SFRSTCR_PIDM = student.PIDM
                    INNER JOIN IDENT_MASTER professor
                        ON SIRASGN_PIDM = professor.PIDM
                    INNER JOIN GTVSDAX
                        ON SFRSTCR_TERM_CODE = GTVSDAX_EXTERNAL_CODE
                        AND GTVSDAX_INTERNAL_CODE_GROUP in ('ALIAS_UP', 'ALIAS_UP_XCRS', 'ALIAS_UR')
                    WHERE
                    (SELECT MAX(SCBCRSE_EFF_TERM)
                        FROM SCBCRSE y
                        WHERE y.SCBCRSE_CRSE_NUMB = x.SCBCRSE_CRSE_NUMB
                        AND y.SCBCRSE_SUBJ_CODE = x.SCBCRSE_SUBJ_CODE
                    ) = x.SCBCRSE_EFF_TERM
                    ORDER BY alias asc
    """.as[(String, String,String, String, String, String)]

  val result = Await.result(db.run(data), Duration.Inf)
    .map(tuple => ClassGroupMember(tuple._1, tuple._2, tuple._3, tuple._4, tuple._5, tuple._6)).take(1000)

  val groups = result.map{
    groupMember =>
      Group(groupMember.courseName, groupMember.courseEmail)
  }.distinct



  def createGroups[T[Group] <: Seq[Group]]( groups: T[Group]) = {

    def CreateGroupInGroupMaster(group: Group): Int = {
      val record = GroupMaster_R(group.id.getOrElse("IMAGINARY LIES"),
        "Y",
        group.name,
        group.email,
        group.directMemberCount.getOrElse(0),
        group.description,
        None,
        Some("COURSE"),
        Some(group.email.replace("@eckerd.edu", "")),
        Some(term)
      )

      if(action == "prod") {
        val action = db.run(GROUP_MASTER_TABLEQUERY.insertOrUpdate(record))
        val groupCreationResult = Await.result(action, Duration(3, "seconds"))
        groupCreationResult
      } else {
        val action = db.run(GROUP_MASTER_TABLEQUERY.filter(rec => rec.id === record.id && rec.email === record.email ).result)
        val groupCreationResult = Await.result(action, Duration(3, "seconds"))
        groupCreationResult.length
      }
    }

    def CreateGroupInGoogle(group: Group): Group = {
      if (action == "prod") {
        adminDirectory.groups.create(group)
      } else {
        adminDirectory.groups.get(group).getOrElse(Group("Not Real", "liedto@eckerd.edu"))
      }
    }

    val finalResult = groups.map{group =>
      val returned = CreateGroupInGoogle(group)
      val createdInDb = CreateGroupInGroupMaster(returned)
      (returned, createdInDb)
    }

    finalResult
  }

  val groupsNow = createGroups(groups)
  groupsNow.foreach(println)
  println("GroupsNow Length- ", groupsNow.length)

}
