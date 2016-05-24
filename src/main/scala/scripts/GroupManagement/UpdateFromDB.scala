package scripts.GroupManagement

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import edu.eckerd.google.api.services.directory.Directory
import edu.eckerd.google.api.services.directory.models.Group
import persistence.entities.tables.GROUP_MASTER
import persistence.entities.tables.GROUPTOIDENT
import persistence.entities.representations.Group2Ident_R
import edu.eckerd.google.api.services.directory.models.Member
import persistence.entities.representations.GroupMaster_R
import slick.driver.JdbcProfile
import utils.configuration.ConfigurationModuleImpl
import utils.persistence.PersistenceModuleImpl

import language.higherKinds
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

/**
  * Created by davenpcm on 4/29/16.
  */
object UpdateFromDB extends App{
  val action = "prod"
  val term = "201530"
  val dataChoice = "debug"

  val modules = new ConfigurationModuleImpl with PersistenceModuleImpl
  implicit val db = modules.db
  import modules.dbConfig.driver.api._
  val GROUP_MASTER_TABLEQUERY = TableQuery[GROUP_MASTER]
  val GROUPTOIDENT_TABLEQUERY = TableQuery[GROUPTOIDENT]

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
                        lower(SSBSECT_SUBJ_CODE) || SSBSECT_CRSE_NUMB || '-' || substr(SSBSECT_SEQ_NUMB, -1) || '-' ||
                         decode(substr(SFRSTCR_TERM_CODE, -2, 1), 1, 'fa', 2, 'sp', 3, 'su') || '@eckerd.edu' as alias,
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


  val membersFromDB = if (dataChoice == "prod"){
    Await.result(db.run(data), Duration.Inf)
      .map(tuple => ClassGroupMember(tuple._1, tuple._2, tuple._3, tuple._4, tuple._5, tuple._6))
  } else {
    Seq(
      ClassGroupMember(
        "TestCourseScala", "TestCourseScala@test.eckerd.edu",
        "1171765", "davenpcm@eckerd.edu",
        "297", "abneyfl@eckerd.edu"
      )
    )
  }

  val groups = membersFromDB.map{
    groupMember =>
      Group(groupMember.courseName, groupMember.courseEmail)
  }.distinct.par.map{ group =>
    val groupMembers = membersFromDB.filter(_.courseEmail.toUpperCase == group.email.toUpperCase)
      .map(classGroupMember =>
        Member(Some(classGroupMember.studentEmail))
      ).toList
    val professor =
      Member(
        Option(membersFromDB.find(_.courseEmail.toUpperCase == group.email.toUpperCase).get).map(_.professorEmail),
        None,
        "OWNER"
    )

    group.copy(members = Some(professor :: groupMembers))
  }.seq




  def createGroups[T[Group] <: Seq[Group]](groups: T[Group],
                                           directory: Directory,
                                           tableQuery: TableQuery[GROUP_MASTER],
                                           db: JdbcProfile#Backend#Database,
                                           action: String ): Seq[(Try[Group], Option[Int])] = {
    //The Vars Are Only Here For Debugging To Create Unique Ids
    var memberNum = 0
    var groupNum = 0
    def CreateGroupInGoogle(group: Group): Try[Group] = {

      if (action == "prod") {
        val createdGroup = Try(directory.groups.create(group)).map(_.copy(members = group.members)) recoverWith  {
          case e: GoogleJsonResponseException if e.getMessage contains "Entity already exists." =>  Try(group)
        }

        createdGroup
      } else {
        Try{
          val returnedGroup = group //directory.groups.get(group).get
          groupNum += 1
          val returnedGroupMembers = group.members.get.map{member => memberNum += 1; member.copy(id = Some(s"TestMemberIDScala$memberNum"))}
          returnedGroup.copy(members = Some(returnedGroupMembers), id = Some(s"TestGroupIDScala$groupNum"))
        }
      }
    }

    def CreateGroupInGroupMaster(tryGroup: Try[Group]): Option[Int] = {
      tryGroup match {
        case Success(group) =>

          val record = GroupMaster_R (group.id.get,
            "Y",
            group.name,
            group.email,
            group.directMemberCount.getOrElse (0),
            group.description,
            None,
            Some ("COURSE"),
            Some (group.email.replace ("@eckerd.edu", "") ),
            Some (term)
          )

          if (action == "prod") {
            val actionHere = db.run (tableQuery += record )
            val groupCreationResult = Option(Await.result (actionHere, Duration (3, "seconds") ))
            groupCreationResult
          } else {
            val actionHere = db.run (tableQuery.filter (rec => rec.id === record.id && rec.email === record.email).result)
            val groupCreationResult = Option(Await.result (actionHere, Duration (3, "seconds") ).length)
            groupCreationResult
          }
        case Failure(e) => None
      }
    }



    val finalResult = groups.map{group =>
      val returned = CreateGroupInGoogle(group)
      val createdInDb = CreateGroupInGroupMaster(returned)
      (returned, createdInDb)
    }

    finalResult
  }

  def createGroupMembers
  ( tryGroups: Seq[Try[Group]],
    directory: Directory,
    tableQuery: TableQuery[GROUPTOIDENT],
    db: JdbcProfile#Backend#Database,
    action: String
  ) = {
    def createInGoogle(tryGroup: Try[Group]): Seq[(Try[Group], Try[Member])] = {
     tryGroup match {
       case Success(group) =>

         if (action == "prod"){
           val members = group.members
             .getOrElse(List[Member]())
             .map(
               member => Try(directory.members.add(group, member)) recoverWith  {
                case e: GoogleJsonResponseException if e.getMessage contains "Entity already exists." => Try(member)}
             )
           members.map((Try(group), _))
         } else {
           val members = group.members.getOrElse(List[Member]())
             .map(member => Try(member))
           members.map((Try(group), _))
         }

       case Failure(e) => Seq((Failure(e), Failure(e)))
     }
    }

    def createInDatabase(tryMembers: (Try[Group], Try[Member])) = {
      tryMembers match {
        case (Success(group), Success(member)) =>
          val ident = Group2Ident_R(
            group.id.get,
            member.id.get,
            "Y",
            member.role,
            member.memberType
          )

          if (action == "prod"){
            val actionHere = db.run(tableQuery += ident )
            Option(Await.result (actionHere, Duration (3, "seconds") ))
          } else {
            val actionHere = db.run(tableQuery.filter (rec => rec.groupId === ident.groupId && rec.identID === ident.identID).result)
            Option(Await.result (actionHere, Duration (3, "seconds") ).length)
          }
        case (Success(group), Failure(e)) => None
        case (Failure(e), _)  => None
      }
    }

    val fromGoogle = tryGroups.flatMap(createInGoogle)

    fromGoogle.map(start => (start, createInDatabase(start)))
  }

  val groupsNow = createGroups(groups, adminDirectory, GROUP_MASTER_TABLEQUERY, db, action)
  groupsNow.foreach(println)
  println("GroupsNow Length- ", groupsNow.length)
  val groupTrys = groupsNow.map(_._1)
  val groupMembersNow = createGroupMembers(groupTrys, adminDirectory, GROUPTOIDENT_TABLEQUERY, db, action)
  groupMembersNow.foreach(println)


//  if (action == "prod" && dataChoice != "prod"){
//    db.run()
//  }

}
