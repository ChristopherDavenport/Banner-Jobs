package google.services.admin.directory.models


/**
  * Created by davenpcm on 5/6/16.
  */
case class User(name: Name,
                primaryEmail: Email,
                password: Option[String] = None,
                id: Option[String] = None,
                orgUnitPath: String = "/",
                agreedToTerms: Option[Boolean] = Some(false),
                changePasswordAtNextLogin: Boolean = false,
                includeInGlobalAddressList: Boolean = true,
                ipWhiteListed: Boolean = false,
                isAdmin: Boolean = false,
                isMailboxSetup: Boolean = false,
                suspended: Boolean = false
               )