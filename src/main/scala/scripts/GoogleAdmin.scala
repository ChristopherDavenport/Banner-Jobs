package scripts

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.admin.directory.{Directory, DirectoryScopes}
import com.google.api.services.admin.directory.model._

import collection.JavaConverters._
import scala.annotation.tailrec
import persistence.entities.representations.GoogleIdentity

import scala.util.{Try, Success, Failure}


/**
  * Created by davenpcm on 4/19/2016.
  */
object GoogleAdmin{

  /**
    * This function is currently configured for a service account to interface with the school. It has been granted
    * explicitly this single grant so to change the scope these need to be implemented in Google first.
    *
    * @return A Google Directory Object which can be used to look through users with current permissions
    */
  private def getDirectoryService(DirectoryScope:String): Directory = {
    import java.util._

    val SERVICE_ACCOUNT_EMAIL = "wso2-admin2@ellucian-identity-service-1282.iam.gserviceaccount.com"
    val SERVICE_ACCOUNT_PKCS12_FILE_PATH = "C:/Users/davenpcm/Downloads/Ellucian Identity Service-8c565b08687e.p12"
    val APPLICATION_NAME = "Ellucian Identity Service"
    val USER_EMAIL = "wso_admin@eckerd.edu"

    val httpTransport = new NetHttpTransport()
    val jsonFactory = new JacksonFactory

    val credential = new GoogleCredential.Builder()
      .setTransport( httpTransport)
      .setJsonFactory(jsonFactory)
      .setServiceAccountId(SERVICE_ACCOUNT_EMAIL)
      .setServiceAccountScopes( Collections.singleton( DirectoryScope ) )
      .setServiceAccountPrivateKeyFromP12File(
        new java.io.File(SERVICE_ACCOUNT_PKCS12_FILE_PATH)
      )
      .setServiceAccountUser(USER_EMAIL)
      .build()

    if (!credential.refreshToken()) {
      throw new RuntimeException("Failed OAuth to refresh the token")
    }

    val directory = new Directory.Builder(httpTransport, jsonFactory, credential)
      .setApplicationName(APPLICATION_NAME)
      .setHttpRequestInitializer(credential)
      .build()

    directory
  }

  /**
    * This is a simple user accumulation function that collects all users in eckerd. It does no additional modification
    * so that you can easily move from users to whatever other form you would like and do those pieces of data
    * manipulation after all the users have been returned.
    *
    * @param service A directory to check
    * @param pageToken A page token which indicates where you are in the sequence of pages of users
    * @param users A List Used For Recursive Accumulation through the function. We build a large list of users
    * @return
    */
  @tailrec
  def listAllUsers(service: Directory = getDirectoryService(DirectoryScopes.ADMIN_DIRECTORY_USER),
                   pageToken: String = "",
                   users: List[User] = List[User]()): List[User] = {

    val result = service.users()
      .list()
      .setDomain("eckerd.edu")
      .setMaxResults(500)
      .setOrderBy("email")
      .setPageToken(pageToken)
      .execute()

    val typedList = List[Users](result)
      .map(users => Option(users.getUsers))
      .map{ case Some(javaList) => javaList.asScala.toList case None => List[User]()}
      .foldLeft(List[User]())((acc, listUsers) => listUsers ::: acc)

    val list = typedList ::: users


    val nextPageToken = result.getNextPageToken
    if (nextPageToken != null && result.getUsers != null) listAllUsers(service, nextPageToken, list) else list

  }

  @tailrec
  def listAllGroups(service: Directory = getDirectoryService(DirectoryScopes.ADMIN_DIRECTORY_GROUP),
                    pageToken: String = "",
                    groups: List[Group] = List[Group]()
                   ): List[Group] ={

    val result = service.groups()
      .list()
      .setDomain("eckerd.edu")
      .setMaxResults(500)
      .setPageToken(pageToken)
      .execute()

    val typedList = List[Groups](result)
      .map(groups => groups.getGroups.asScala.toList)
      .foldLeft(List[Group]())((acc, listGroups) => listGroups ::: acc)

    val list = typedList ::: groups

    val nextPageToken = result.getNextPageToken

    if (nextPageToken != null && result.getGroups != null) listAllGroups(service, nextPageToken, list) else list

  }

  @tailrec
  def listAllGroupMembers(groupKey: String,
                          service: Directory = getDirectoryService(DirectoryScopes.ADMIN_DIRECTORY_GROUP),
                          pageToken: String = "",
                          members: List[Member] = List[Member]()
                          ): List[Member] = {

    val resultTry = Try(
      service.members()
      .list(groupKey)
      .setMaxResults(500)
      .setPageToken(pageToken)
      .execute()
    )

    resultTry match {
      case Success(result) =>
        val typedList = List[Members](result)
          .map(members => Option(members.getMembers))
          .map{ case Some(member) => member.asScala.toList case None => List[Member]() }
          .foldLeft(List[Member]())((acc, listMembers) => listMembers ::: acc)

        val list = typedList ::: members

        val nextPageToken = result.getNextPageToken

        if (nextPageToken != null && result.getMembers != null) listAllGroupMembers(groupKey, service, nextPageToken, list) else list

      case Failure(exception: GoogleJsonResponseException) => listAllGroupMembers(groupKey, service, pageToken, members)
      case Failure(e) => throw e
    }

  }



  /**
    * This function is a Type free implementation that allows you to tell you what type you are expecting as a return
    * and it will fully type check that you are getting the type you want back.
    *
    * @param service This is the directory to be returned
    * @param pageToken This is a page token to show where in the series of pages you are.
    * @param transformed A List of Type T, is Initialized to an Empty List that is expanded recursively through each
    *                    of the pages
    * @param f This is a transformation function that takes a User and Transforms it to Type T
    * @tparam T This is any type that you want to return from the google users.
    * @return Returns a List of Type T
    */
  @tailrec
  private def transformAllGoogleUsers[T](service: Directory = getDirectoryService(DirectoryScopes.ADMIN_DIRECTORY_USER),
                                    pageToken: String = "",
                                    transformed: => List[T] = List[T]()
                                   )(f: User => T): List[T] = {

    val result = service.users()
      .list()
      .setDomain("eckerd.edu")
      .setMaxResults(500)
      .setOrderBy("email")
      .setPageToken(pageToken)
      .execute()

    lazy val typedList = List[Users](result)
      .map(users => Option(users.getUsers))
      .map{ case Some(javaList) => javaList.asScala.toList case None => List[User]()}
      .foldLeft(List[User]())((acc, listUsers) => listUsers ::: acc)
      .map(user => f(user))

    lazy val list: List[T] = typedList ::: transformed

    val nextPageToken = result.getNextPageToken

    if (nextPageToken != null && result.getUsers != null) transformAllGoogleUsers(service, nextPageToken, list)(f) else list
  }



  /**
    * Returns a List of Google Identities for Eckerd College
    * @return List of GoogleIdentity
    */
  def ReturnAllGoogleIdentities(): List[GoogleIdentity] = {

    /**
      * Simple Transformation between the Google User Objects ID and Primary Email and a GoogleIdentity Case Class
      * @param user Google User Object
      * @return A GoogleIdentity
      */
    def UserToGoogleIdent(user: User): GoogleIdentity = {
      GoogleIdentity(user.getId, user.getPrimaryEmail)
    }

    val googleIdentitiesSets = transformAllGoogleUsers[GoogleIdentity]()(UserToGoogleIdent)

    googleIdentitiesSets
  }

  val groupScope = DirectoryScopes.ADMIN_DIRECTORY_GROUP
  val groupMemberScope = DirectoryScopes.ADMIN_DIRECTORY_GROUP_MEMBER


}
