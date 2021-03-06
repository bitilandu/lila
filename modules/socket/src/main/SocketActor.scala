package lila.socket

import scala.concurrent.duration._
import scala.util.Random

import akka.actor.{ Deploy ⇒ _, _ }
import play.api.libs.json._

import actorApi._
import lila.hub.actorApi.{ Deploy, GetUids, WithUserIds, GetNbMembers, NbMembers, SendTo, SendTos }
import lila.hub.actorApi.round.MoveEvent
import lila.memo.ExpireSetMemo

abstract class SocketActor[M <: SocketMember](uidTtl: Duration) extends Socket with Actor {

  var members = Map.empty[String, M]
  val aliveUids = new ExpireSetMemo(uidTtl)
  var pong = makePong(0)

  // to be defined in subclassing actor
  def receiveSpecific: Receive

  // generic message handler
  def receiveGeneric: Receive = {

    case Ping(uid)                  ⇒ ping(uid)

    case Broom                      ⇒ broom

    // when a member quits
    case Quit(uid)                  ⇒ quit(uid)

    case GetNbMembers               ⇒ sender ! members.size

    case NbMembers(nb)              ⇒ pong = makePong(nb)

    case WithUserIds(f)             ⇒ f(userIds)

    case GetUids                    ⇒ sender ! uids

    case LiveGames(uid, gameIds)    ⇒ registerLiveGames(uid, gameIds)

    case move: MoveEvent            ⇒ notifyMove(move)

    case SendTo(userId, msg)        ⇒ sendTo(userId, msg)

    case SendTos(userIds, msg)      ⇒ sendTos(userIds, msg)

    case Resync(uid)                ⇒ resync(uid)

    case Deploy(event, html)        ⇒ broadcast(makeMessage(event.key, html))
  }

  def receive = receiveSpecific orElse receiveGeneric

  override def postStop() {
    members.values foreach { _.channel.end() }
  }

  def notifyAll[A: Writes](t: String, data: A) {
    val msg = makeMessage(t, data)
    members.values.foreach(_.channel push msg)
  }

  def notifyMember[A: Writes](t: String, data: A)(member: M) {
    member.channel push makeMessage(t, data)
  }

  def makePong(nb: Int) = makeMessage("n", nb)

  def ping(uid: String) {
    setAlive(uid)
    withMember(uid)(_.channel push pong)
  }

  def sendTo(userId: String, msg: JsObject) {
    memberByUserId(userId) foreach (_.channel push msg)
  }

  def sendTos(userIds: Set[String], msg: JsObject) {
    membersByUserIds(userIds) foreach (_.channel push msg)
  }

  def broom {
    members.keys filterNot aliveUids.get foreach eject
  }

  def eject(uid: String) {
    withMember(uid) { member ⇒
      member.channel.end()
      quit(uid)
    }
  }

  def quit(uid: String) {
    members = members - uid
  }

  def broadcast(msg: JsObject) {
    members.values foreach (_.channel push msg)
  }

  private val resyncMessage = makeMessage("resync", JsNull)

  protected def resync(member: M) {
    import play.api.libs.concurrent._
    import play.api.Play.current
    import scala.concurrent.duration._

    Akka.system.scheduler.scheduleOnce((Random nextInt 2000).milliseconds) {
      resyncNow(member)
    }
  }

  protected def resync(uid: String) {
    withMember(uid)(resync)
  }

  protected def resyncNow(member: M) {
    member.channel push resyncMessage
  }

  def addMember(uid: String, member: M) {
    eject(uid)
    members = members + (uid -> member)
    setAlive(uid)
  }

  def setAlive(uid: String) { aliveUids put uid }

  def uids = members.keys

  def memberByUserId(userId: String): Option[M] =
    members.values find (_.userId == Some(userId))

  def membersByUserIds(userIds: Set[String]): Iterable[M] =
    members.values filter (member ⇒ member.userId ?? userIds.contains)

  def userIds: Iterable[String] = members.values.map(_.userId).flatten

  def notifyMove(move: MoveEvent) {
    lazy val msg = makeMessage("fen", JsObject(Seq(
      "id" -> JsString(move.gameId),
      "fen" -> JsString(move.fen),
      "lm" -> JsString(move.move)
    )))
    members.values filter (_ liveGames move.gameId) foreach (_.channel push msg)
  }

  def showSpectators(users: List[String], nbAnons: Int) = nbAnons match {
    case 0 ⇒ users
    case x ⇒ users :+ ("Anonymous (%d)" format x)
  }

  def registerLiveGames(uid: String, ids: List[String]) {
    withMember(uid)(_ addLiveGames ids)
  }

  def withMember(uid: String)(f: M ⇒ Unit) {
    members get uid foreach f
  }
}
