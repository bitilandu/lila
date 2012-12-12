package lila
package team

import core.Settings
import site.Captcha
import user.UserRepo
import message.LichessThread

import com.mongodb.casbah.MongoCollection
import scalaz.effects._

final class TeamEnv(
    settings: Settings,
    captcha: Captcha,
    userRepo: UserRepo,
    sendMessage: LichessThread ⇒ IO[Unit],
    mongodb: String ⇒ MongoCollection) {

  import settings._

  lazy val teamRepo = new TeamRepo(mongodb(TeamCollectionTeam))

  lazy val memberRepo = new MemberRepo(mongodb(TeamCollectionMember))

  lazy val requestRepo = new RequestRepo(mongodb(TeamCollectionRequest))

  private lazy val messenger = new TeamMessenger(
    send = sendMessage,
    netBaseUrl = NetBaseUrl)

  lazy val paginator = new PaginatorBuilder(
    memberRepo = memberRepo,
    teamRepo = teamRepo,
    userRepo = userRepo,
    maxPerPage = TeamPaginatorMaxPerPage)

  lazy val api = new TeamApi(
    teamRepo = teamRepo,
    memberRepo = memberRepo,
    requestRepo = requestRepo,
    userRepo = userRepo,
    messenger = messenger,
    paginator = paginator)

  lazy val forms = new DataForm(teamRepo, captcha)
}