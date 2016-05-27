package scripts.GroupManagement

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import edu.eckerd.google.api.services.directory.Directory
import edu.eckerd.google.api.services.directory.models.Group
import persistence.entities.tables.{GOOGLE_USERS, GROUPTOIDENT, GROUP_MASTER}
import persistence.entities.representations.Group2Ident_R
import edu.eckerd.google.api.services.directory.models.Member
import persistence.entities.representations.GroupMaster_R
import slick.driver.JdbcProfile
import utils.configuration.ConfigurationModuleImpl
import utils.persistence.PersistenceModuleImpl

import scala.concurrent.ExecutionContext.Implicits.global
import language.higherKinds
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

/**
  * Created by davenpcm on 4/29/16.
  */
object UpdateFromDB extends App{
  val action = "debug"
//  val term = "201530"
  val dataChoice = "prod"

  val modules = new ConfigurationModuleImpl with PersistenceModuleImpl
  implicit val db = modules.db
  import modules.dbConfig.driver.api._
  val GROUP_MASTER_TABLEQUERY = TableQuery[GROUP_MASTER]
  val GROUPTOIDENT_TABLEQUERY = TableQuery[GROUPTOIDENT]
  val GOOGLE_TABLEQUERY = TableQuery[GOOGLE_USERS]

  implicit val adminDirectory = Directory()

  case class ClassGroupMember (
                                courseName: String,
                                courseEmail: String,
                                studentAccountId: String,
                                studentEmail: String,
                                fallbackAccountID: Option[String],
                                fallbackEmail: Option[String],
                                professorAccountId: String,
                                professorEmail: String,
                                term : String
                              )


  val data = sql"""SELECT
                    --     SSBSECT_CRN as CRN,
                    --     SSBSECT_SEQ_NUMB as SEQNO,
                    --     SSBSECT_SUBJ_CODE as SUBJECT,
                    --     SSBSECT_CRSE_NUMB as COURSE_NUMBER,
                    --     substr(SSBSECT_SEQ_NUMB, -1),
                    --     decode(substr(SFRSTCR_TERM_CODE, -2, 1), 1, 'fa', 2, 'sp', 3, 'su') as TERM_ALIAS,
  nvl(SSBSECT_CRSE_TITLE, x.SCBCRSE_TITLE) as COURSE_TITLE,
  lower(SSBSECT_SUBJ_CODE) || SSBSECT_CRSE_NUMB || '-' || TO_CHAR(TO_NUMBER(SSBSECT_SEQ_NUMB)) ||
  '-' || decode(substr(SFRSTCR_TERM_CODE, -2, 1), 1, 'fa', 2, 'sp', 3, 'su') || '@eckerd.edu' as ALIAS,
  student.PRIMARY_EMAIL_ID as STUDENT_ACCOUNT,
  student.PRIMARY_EMAIL as STUDENT_EMAIL,
  g.GOOGLE_ID as FALLBACK_ID,
  g.EMAIL AS FALLBACK_EMAL,
--   emal.GOREMAL_EMAIL_ADDRESS as FALLBACK_EMAIL,
  professor.PRIMARY_EMAIL_ID as PROFESSOR_ACCOUNT,
  professor.PRIMARY_EMAIL as PROFESSOR_EMAIL,
  GTVSDAX_EXTERNAL_CODE
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
       AND REGEXP_LIKE(SSBSECT_SEQ_NUMB, '^-?\d+?$$')
  LEFT JOIN SIRASGN
    ON SIRASGN_TERM_CODE = SFRSTCR_TERM_CODE
       AND SIRASGN_CRN = SFRSTCR_CRN
       AND SIRASGN_PRIMARY_IND = 'Y'
       AND SIRASGN_PIDM is not NULL
  INNER JOIN STVRSTS
    ON STVRSTS_CODE = SFRSTCR_RSTS_CODE
       AND STVRSTS_INCL_SECT_ENRL = 'Y'
       AND STVRSTS_WITHDRAW_IND = 'N'
  INNER JOIN IDENT_MASTER student
    INNER JOIN GOREMAL emal
      ON GOREMAL_PIDM = student.PIDM
      AND GOREMAL_EMAL_CODE IN ('CAS', 'ZCAS')
    LEFT JOIN GOOGLE_USERS g
      ON g.FIRST_NAME = student.FIRST_NAME
      AND g.LAST_NAME = student.LAST_NAME
      AND emal.GOREMAL_EMAIL_ADDRESS = g.EMAIL
      AND g.EMAIL <> student.PRIMARY_EMAIL
      AND g.GOOGLE_ID <> student.PRIMARY_EMAIL_ID
    ON SFRSTCR_PIDM = student.PIDM
  INNER JOIN IDENT_MASTER professor
    ON SIRASGN_PIDM = professor.PIDM
  INNER JOIN (
    SELECT DISTINCT GTVSDAX_EXTERNAL_CODE
    FROM GTVSDAX
    WHERE
      GTVSDAX_INTERNAL_CODE = 'ECTERM'
      AND GTVSDAX_INTERNAL_CODE_GROUP IN ('ALIAS_UP', 'ALIAS_UP_XCRS', 'ALIAS_UR', 'ALIAS_UR_XCRS')
    ) ON SFRSTCR_TERM_CODE = GTVSDAX_EXTERNAL_CODE
WHERE
  (SELECT MAX(SCBCRSE_EFF_TERM)
   FROM SCBCRSE y
   WHERE y.SCBCRSE_CRSE_NUMB = x.SCBCRSE_CRSE_NUMB
         AND y.SCBCRSE_SUBJ_CODE = x.SCBCRSE_SUBJ_CODE
  ) = x.SCBCRSE_EFF_TERM
ORDER BY alias asc
    """.as[(String, String,String, String, Option[String], Option[String], String, String, String)]

  val existingGroupMembersQuery = db.run(sql"""
                                     SELECT
                                      u.EMAIL,
                                      g.EMAIL
                                     FROM GROUP_TO_IDENT gti
                                     INNER JOIN GROUP_MASTER g
                                       on gti.GROUP_ID = g.ID
                                     INNER JOIN GOOGLE_USERS u
                                       on gti.IDENT_ID = u.GOOGLE_ID
    """.as[(String, String)])

  val existingSet = Await.result(existingGroupMembersQuery, Duration(60, "seconds"))
    .map(v => (v._1.toLowerCase, v._2.toLowerCase))
    .toSet

  val membersFromDB = if (dataChoice == "prod"){
    val fromDB = Await.result(db.run(data), Duration.Inf)

    println( "Initial Query Size", fromDB.length)
    val intermediateResult = fromDB
      .map(tuple => ClassGroupMember(tuple._1, tuple._2, tuple._3, tuple._4, tuple._5, tuple._6, tuple._7, tuple._8, tuple._9))
//    intermediateResult.foreach(println)
    val result = intermediateResult
      .filterNot(fromQuery =>
        existingSet((fromQuery.studentEmail.toLowerCase, fromQuery.courseEmail.toLowerCase) ) ||
        existingSet((fromQuery.fallbackEmail.getOrElse("BAD-DONT-MATCH").toLowerCase, fromQuery.courseEmail.toLowerCase))
      )

    result
  } else {
    Seq(ClassGroupMember(
        "TestCourseScala", "TestCourseScala@test.eckerd.edu",
        "1171765", "davenpcm@eckerd.edu", None, None,
        "297", "abneyfl@eckerd.edu", "201530"
      ))
      .filterNot(fromQuery =>
        existingSet((fromQuery.studentEmail.toLowerCase, fromQuery.courseEmail.toLowerCase) )
      )
  }

  println("Values Which Dont Exist", membersFromDB.length)


  val groups = membersFromDB.map{
    groupMember =>
      ( Group(groupMember.courseName, groupMember.courseEmail), groupMember.term)
  }.distinct.par.map{ group =>
    val groupMembers = membersFromDB.filter(_.courseEmail.toLowerCase == group._1.email.toLowerCase)
      .map(classGroupMember => classGroupMember.fallbackEmail match {
        case Some(email) => Member(Some(email))
        case None => Member(Some(classGroupMember.studentEmail))
      }

      ).toList
    val professor =
      Member(
        Option(membersFromDB.find(_.courseEmail.toLowerCase == group._1.email.toLowerCase).get).map(_.professorEmail),
        None,
        "OWNER"
    )

    (group._1.copy(members = Some(professor :: groupMembers)), group._2)
  }.seq



  def createGroups[T[Group] <: Seq[Group]](groups: T[(Group, String)],
                                           directory: Directory,
                                           tableQuery: TableQuery[GROUP_MASTER],
                                           db: JdbcProfile#Backend#Database,
                                           action: String ): Seq[(Try[Group], Option[Int])] = {

    def CheckIfExists(group: Group): Group = {
      val checkedGroup = tableQuery
        .filter(rec => rec.email.toLowerCase === group.email.toLowerCase)
        .result
      val result = db.run(checkedGroup)
      val action = result.map(_.headOption.map(rec => group.copy(id = Some(rec.id))).getOrElse(group))
      Await.result(action, Duration(1, "second"))
    }

    //The Vars Are Only Here For Debugging To Create Unique Ids
    var memberNum = 0
    var groupNum = 0
    def CreateGroupInGoogle(group: Group): Try[Group] = {
      group.id match {
        case Some(_) =>  if (action == "prod") Try(group) else  {
          val debugMemberID = group.members.get.map { member => memberNum += 1; member.copy(id = Some(s"TestMemberIDScala$memberNum")) }
          Try(group.copy(members = Some(debugMemberID)))
        }
        case None =>
          if (action == "prod") {
            val createdGroup = Try(directory.groups.create(group)).map(_.copy(members = group.members))

            createdGroup
          } else {
            Try {
              val returnedGroup = group //directory.groups.get(group).get
              groupNum += 1
              val returnedGroupMembers = group.members.get.map { member => memberNum += 1; member.copy(id = Some(s"TestMemberIDScala$memberNum")) }
              val copied = returnedGroup.copy(members = Some(returnedGroupMembers), id = Some(s"TestGroupIDScala$groupNum"))
              copied
            }
          }
      }
    }

    def CreateGroupInGroupMaster(tryGroup: Try[Group], term: String): Option[Int] = {
      tryGroup match {
        case Success(group) =>
            val record = GroupMaster_R ( group.id match {
              case Some(id) => id
              case None => if (action == "prod") throw new Throwable("No ID Returned From Google") else "0"
            },
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
      val checkedGroups = CheckIfExists(group._1)

      val returned = CreateGroupInGoogle(checkedGroups)
      val createdInDb = CreateGroupInGroupMaster(returned, group._2)
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

  val groupsNow = createGroups(groups, adminDirectory, GROUP_MASTER_TABLEQUERY, db, action).map(_._1).filter(_.isSuccess)
  val split = groupsNow.map(_.get).partition(_.id.get contains "Test")
  val groupProbs = split._1
  val personProbs = split._2
  println("Group Probs Length", groupProbs.length)
  println("Person Probs Length", personProbs.length)
  println
  println("Group Problems")
  groupProbs.foreach(println)
  println
  println("Person Problems")
  personProbs.foreach(println)

  val membersNow = createGroupMembers(groupsNow, adminDirectory, GROUPTOIDENT_TABLEQUERY, db, action)
  membersNow.map(t => (t._1._1.get.email, t._1._2.get.email.get)).foreach(println)
//  groupsNow.foreach(println)
//  println("GroupsNow Length ", groupsNow.length)
//  val groupTrys = groupsNow.map(_._1)
//  val groupMembersNow = createGroupMembers(groupTrys, adminDirectory, GROUPTOIDENT_TABLEQUERY, db, action)
//  groupMembersNow.foreach(println)


//  if (action == "prod" && dataChoice != "prod"){
//    db.run()
//  }

//  Await.result(existingGroupMembersQuery, Duration.Inf).foreach(println)
}
