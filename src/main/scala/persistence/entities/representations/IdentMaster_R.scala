package persistence.entities.representations

/**
  * Created by davenpcm on 5/24/16.
  */
case class IdentMaster_R(
                             Username: String,
                             Pidm: Int,
                             personalInfo: PersonalInfo,
                             businessInfo: BussinessInfo,
                             facultyInfo: FacultyInfo,
                             studentInfo: StudentInfo
                           )
case class PersonalInfo(
                         EckerdId: Option[String],
                         FirstName: Option[String],
                         LastName: Option[String],

                         Email: Option[String]
                       )

case class BussinessInfo(
                          EnterpriseUsername: Option[String],

                          EmployeeClass: Option[String],
                          EmployeeStatus: Option[String],
                          HomeOrg: Option[String],

                          JobOrg: Option[String],
                          JobPosn: Option[String],

                          Roles: Option[String]
                        )

case class FacultyInfo(
                        FacultyStatus: Option[String],
                        FacultyTag: Option[String],
                        FacultySchdInd: Option[String],
                        FacultyAdvisorInd: Option[String],

                        FacultyType: Option[String]
                      )

case class StudentInfo(
                        StudentStatus: Option[String],
                        StudentLevel: Option[String],
                        StudentClass: Option[String],

                        Majors: Option[String],
                        Minors: Option[String]
                      )
