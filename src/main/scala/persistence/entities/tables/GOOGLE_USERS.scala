package persistence.entities.tables
import com.typesafe.slick.driver.oracle.OracleDriver.api._
import edu.eckerd.google.api.services.directory.models.User
import edu.eckerd.google.api.services.directory.models.Email
import edu.eckerd.google.api.services.directory.models.Name

/**
  * Created by davenpcm on 5/26/16.
  */


class GOOGLE_USERS(tag: Tag)  extends Table[User](tag, "GOOGLE_USERS") {
  def googleID = column[String]("GOOGLE_ID", O.PrimaryKey)

  def firstName = column[String]("FIRST_NAME")
  def lastName = column[String]("LAST_NAME")
  def email = column[String]("EMAIL")
  def isPrimaryEmail = column[Boolean]("IS_PRIMARY_EMAIL")
  def password = column[Option[String]]("PASSWORD") // Here Only For Matching
  def orgUnitPath = column[String]("ORG_UNIT_PATH")
  def agreedToTerms = column[Option[Boolean]]("AGREED_TO_TERMS")
  def changePasswordAtNextLogin = column[Boolean]("CHANGE_PASSWORD_NEXT_LOGIN")
  def includeInGlobalAddressList = column[Boolean]("INCLUDE_IN_GLOBAL_ADDRESS_LIST")
  def ipWhiteListed = column[Boolean]("IP_WHITELISTED")
  def isAdmin = column[Boolean]("IS_ADMIN")
  def isMailboxSetup = column[Boolean]("IS_MAILBOX_SETUP")
  def suspended = column[Boolean]("SUSPENDED")

  def * = (
    (
      firstName,
      lastName
      ),
    email,
    password,
    googleID,
    orgUnitPath,
    agreedToTerms,
    changePasswordAtNextLogin,
    includeInGlobalAddressList,
    ipWhiteListed,
    isAdmin,
    isMailboxSetup,
    suspended
    ).shaped <> (
    {
      case (
        name,
        email,
        password,
        id,
        orgUnitPath,
        agreedToTerms,
        changePasswordAtNextLogin,
        includeInGlobalAddressList,
        ipWhiteListed,
        isAdmin,
        isMailboxSetup,
        suspended
        ) =>
        User(
          Name.tupled.apply(name),
          Email(email),
          password,
          Some(id),
          orgUnitPath,
          agreedToTerms,
          changePasswordAtNextLogin,
          includeInGlobalAddressList,
          ipWhiteListed,
          isAdmin,
          isMailboxSetup,
          suspended
        )
    },
    {
      i : User =>
        def f1(p: Name) = Name.unapply(p).get
        Some((
          f1(i.name),
          i.primaryEmail.address,
          i.password,
          i.id.getOrElse("BAD ID"),
          i.orgUnitPath,
          i.agreedToTerms,
          i.changePasswordAtNextLogin,
          i.includeInGlobalAddressList,
          i.ipWhiteListed,
          i.isAdmin,
          i.isMailboxSetup,
          i.suspended
        ))
    }
    )
}
