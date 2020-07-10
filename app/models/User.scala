package models

import org.mongodb.scala.bson.ObjectId

object User {
  def apply(username: String, password: String, firstName: String, lastName: String, email: String, enabled: Boolean, 
            roles: Seq[String]): User =
    User(new ObjectId(), username, password, firstName, lastName, email, enabled, roles)

  def getUserDetails(user: User): AuthUser = {
    new AuthUser(user.username, user.password, user.enabled, false, false, false, user.roles)
  }
}

case class User(_id: ObjectId, username: String, password: String, firstName: String, lastName: String, email: String,
                enabled: Boolean, roles: Seq[String])

case class FormMessage(content: String)

object Role extends Enumeration {
  type Role = Value
  val ADMIN = Value("ADMIN")
  val USER = Value("USER")
  val GUEST = Value("GUEST")
}
