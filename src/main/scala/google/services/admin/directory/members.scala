package google.services.admin.directory

import google.services.admin.directory.models.Member
import google.services.admin.directory.models.Group

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

/**
  * Created by davenpcm on 5/3/16.
  */
class members(directory: Directory) {
  val service = directory.directory

  /**
    * The function takes a groupKey to return all members of the group. Current Error Handling will terminate on an error
    * from our side, but will retry if it is a bad return response from google, likely a service unavailable. Which
    * occurs sporadically.
    *
    * @param groupKey This is either the unique group id or the group email address. Which specifies which group to
    *                 return the members of
    * @param pageToken This is the page token that shows where we are in the pages.
    * @param members This is the growing list of group members
    * @return A list of all members of the group
    */
  @tailrec
  final def list(groupKey: String,
           pageToken: String = "",
           members: List[Member] = List[Member]()
          ): Either[Throwable, List[Member]] = {
    import scala.collection.JavaConverters._
    import com.google.api.services.admin.directory.model.Members
    
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
          .map(members =>
            if (Option(members.getMembers).isDefined ){
              members.getMembers.asScala.toList}
            else {
              List[com.google.api.services.admin.directory.model.Member]()
            }
          )
          .foldLeft(List[Member]())((acc, listMembers) => listMembers.map(Member.apply) ::: acc)

        val myList = typedList ::: members

        val nextPageToken = result.getNextPageToken

        if (nextPageToken != null && result.getMembers != null) list(groupKey, nextPageToken, myList) else Right(myList)
      case Failure(ex) => ex match {
        case e: com.google.api.client.googleapis.json.GoogleJsonResponseException => list(groupKey, pageToken, members)
        case _ => Left(ex)
      }
    }

  }

  def addById(groupKey: String, newMemberId: String): Member = {

    val newMember = Member(None, Some(newMemberId))

    val InsertedMember = service.members().insert(groupKey, newMember).execute()
    InsertedMember
  }

  def add(groupKey: String, newMemberEmail: String): Member = {
    val newMember = Member(Some(newMemberEmail))
    val InsertedMember = service.members().insert(groupKey, newMember).execute()
    InsertedMember
  }

  def add(groupKey: String, newMember: Member): Member = {
    val InsertedMember = service.members().insert(groupKey, newMember).execute()
    InsertedMember
  }

  def add(group: Group, newMember: Member): Member = {
    val InsertedMember = if ( group.id.isDefined){
      service.members().insert(group.id.get, newMember).execute()
    } else {
      service.members().insert(group.email, newMember).execute()
    }

    InsertedMember
  }

  def remove(groupKey: String, userId: String): Unit = {
    service.members().delete(groupKey, userId).execute()
  }

}
