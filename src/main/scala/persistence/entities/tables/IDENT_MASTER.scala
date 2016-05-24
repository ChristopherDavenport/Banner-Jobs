package persistence.entities.tables

import persistence.entities.representations.IdentMaster_R
import persistence.entities.representations.BussinessInfo
import persistence.entities.representations.PersonalInfo
import persistence.entities.representations.StudentInfo
import persistence.entities.representations.FacultyInfo
import com.typesafe.slick.driver.oracle.OracleDriver.api._


/**
  * Created by davenpcm on 5/24/16.
  */
class IDENT_MASTER (tag: Tag) extends Table[IdentMaster_R](tag, "IDENT_MASTER") {

  def Username = column[String]("USERNAME", O.PrimaryKey)
  def Pidm = column[Int]("PIDM")

  def Email = column[Option[String]]("EMAIL")

  def EckerdId = column[Option[String]]("ECKERD_ID")
  def FirstName = column[Option[String]]("FIRST_NAME")
  def LastName = column[Option[String]]("LAST_NAME")

  def EnterpriseUsername = column[Option[String]]("ENTERPRISE_USERNAME")

  def EmployeeClass = column[Option[String]]("EMPLOYEE_CLASS")
  def EmployeeStatus = column[Option[String]]("EMPLOYEE_STATUS")
  def HomeOrg = column[Option[String]]("HOME_ORG")

  def JobOrg = column[Option[String]]("TIMESHEET_ORG")
  def JobPosn = column[Option[String]]("PRIMARY_JOB")

  def Roles = column[Option[String]]("ROLES")

  def FacultyStatus = column[Option[String]]("FACULTY_STATUS")
  def FacultyTag = column[Option[String]]("FACULTY_TAG")
  def FacultySchdInd = column[Option[String]]("FACULTY_SCHD_IND")
  def FacultyAdvisorInd = column[Option[String]]("FACULTY_ADVR_IND")
  def FacultyType = column[Option[String]]("FACULTY_TYPE")

  def StudentStatus = column[Option[String]]("STUDENT_STATUS")
  def StudentLevel = column[Option[String]]("STUDENT_LEVEL")
  def StudentClass = column[Option[String]]("STUDENT_CLASS")

  def StudentMajors = column[Option[String]]("STUDENT_MAJORS")
  def StudentMinors = column[Option[String]]("STUDENT_MINORS")

  def * = (
    Username,
    Pidm ,
    (EckerdId,
      FirstName,
      LastName,
      Email
      ),
    (EnterpriseUsername,
      EmployeeClass,
      EmployeeStatus,
      HomeOrg,
      JobOrg,
      JobPosn,
      Roles
      ),
    (FacultyStatus,
      FacultyTag,
      FacultySchdInd,
      FacultyAdvisorInd,
      FacultyType
      ),
    (StudentStatus,
      StudentLevel,
      StudentClass,
      StudentMajors,
      StudentMinors
      )
    ).shaped <> (
    {
      case (username, pidm, personalInfo, bussinessInfo, facultyInfo, studentInfo) =>
        IdentMaster_R(
          username,
          pidm,
          PersonalInfo.tupled.apply(personalInfo),
          BussinessInfo.tupled.apply(bussinessInfo),
          FacultyInfo.tupled.apply(facultyInfo),
          StudentInfo.tupled.apply(studentInfo)
        )
    },
    {
      i: IdentMaster_R =>
        def f1(p: PersonalInfo) = PersonalInfo.unapply(p).get
        def f2(p: BussinessInfo) = BussinessInfo.unapply(p).get
        def f3(p: FacultyInfo) = FacultyInfo.unapply(p).get
        def f4(p: StudentInfo) = StudentInfo.unapply(p).get
        Some((i.Username, i.Pidm, f1(i.personalInfo), f2(i.businessInfo), f3(i.facultyInfo), f4(i.studentInfo)))
    }
    )

  def pidmIndex = index("pidm_index", Pidm, unique=true)
}