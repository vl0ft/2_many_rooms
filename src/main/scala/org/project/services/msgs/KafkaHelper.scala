package org.project.services.msgs

import akka.Done
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.model.DateTime
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.Source
import com.typesafe.config.Config
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.project.model.{Message, Room, User}
import spray.json.{CompactPrinter, RootJsonFormat}

import scala.concurrent.Future

object KafkaHelper {

  // Gets the host and a port from the configuration

  def apply(config: Config)(implicit system: ActorSystem[_]): KafkaHelper = {
    val bootstrapServer = config.getString("kafka.bootstrap.servers")

    val consumerSettings: ConsumerSettings[String, String] = ConsumerSettings(system, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers(bootstrapServer)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000")

    val producerSettings: ProducerSettings[String, String] = ProducerSettings(system, new StringSerializer, new StringSerializer)
      .withBootstrapServers(bootstrapServer)

    val kafkaProducer = producerSettings.createKafkaProducer()
    val settingsWithProducer = producerSettings.withProducer(kafkaProducer)

    new KafkaHelper(consumerSettings, settingsWithProducer)
  }

}


class KafkaHelper(consumerSettings: ConsumerSettings[String, String],
                  settingsWithProducer: ProducerSettings[String, String])
                 (implicit system: ActorSystem[_]) {

  import spray.json.DefaultJsonProtocol._

  implicit val msgFormat: RootJsonFormat[Message] = jsonFormat2(Message)

  def pushMsg(room: Room, msg: Message): Future[Done] = {
    Source.single(msg)
      .map(msg => CompactPrinter(msgFormat.write(msg)))
      .map(value => new ProducerRecord[String, String](room.name, DateTime.now.toIsoDateTimeString(), value))
      .runWith(Producer.plainSink(settingsWithProducer))
  }

  def getTopicSource(user: User, room: Room): Source[String, Consumer.Control] = {
    Consumer.plainSource(consumerSettings.withGroupId(user.name), Subscriptions.topics(room.name))
      .map(consumerRecord => consumerRecord.value())
  }
}


