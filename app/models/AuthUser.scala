package models

class AuthUser(val username: String, val password: String, val enabled: Boolean, val accountNonExpired: Boolean,
               val accountNonLocked: Boolean, val credentialsNonExpired: Boolean, val roles: Seq[String]) {

  def getAuthorities(): String = {
    val sb = StringBuilder.newBuilder
    for (role <- this.roles) {
      sb.append(role + ",")
    }
    sb.toString()
  }
}

case class AuthRequest(username: String, password: String)

case class AuthResponse(username: String, token: String)
