package lila.socket

import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.iteratee.{ Iteratee, Enumerator }
import play.api.libs.json._

import ornicar.scalalib.Zero

trait WithSocket {

  type JsChannel = Channel[JsValue]
  type JsEnumerator = Enumerator[JsValue]
  type JsSocketHandler = (Iteratee[JsValue, _], JsEnumerator)

  implicit val LilaJsSocketHandlerZero: Zero[JsSocketHandler] = 
    Zero.instance(Handler errorHandler "default error handler used")
}
