package com.y2.communication_service

import akka.actor.{Actor, ActorLogging, ActorRef, RootActorPath}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, ClusterEvent}
import com.y2.client_service.MessageSequence
import com.y2.messages.ClientCommunicationMessage._

/**
  * Service that handles communication in the y2 cluster.
  */
class CommunicationService extends Actor with ActorLogging with MessageSequence {
  /**
    * The y2 cluster.
    */
  private val cluster = Cluster(context.system)

  /**
    * The client from which to get instructions to execute.
    * Null, when no client ever responded.
    */
  private var client: ActorRef = _

  /**
    * Contains the status of the CommunicationService
    */
  private var status: CommunicationServiceStatus = NoClient

  /**
    * Received data
    */
  private var data: List[Int] = _

  /**
    * When the actor starts it tries to join the cluster.
    * We use cluster bootstrap that automatically tries to discover nodes of the cluster and create a new cluster if
    * none was found.
    */
  override def preStart(): Unit = {
    // Subscribe to MemberUp messages to perform setup actions when the node joins the cluster
    cluster.subscribe(self, ClusterEvent.InitialStateAsEvents, classOf[MemberUp])
  }

  /**
    * Unsubscribe from the cluster when stopping the actor.
    */
  override def postStop(): Unit = cluster.unsubscribe(self)

  /**
    * Handle received messages.
    * @return a function that handles the received messages.
    */
  @Override
  def receive = receiveChunks orElse {

    // Ask to connect to a client if this node does not already have one
    case MemberUp(m) => {
      if (client == null && m.hasRole("client")) {
        // Send a message to all clients connected to the cluster
        context.actorSelection(RootActorPath(m.address) / "user" / "client") ! ClientRequest
      }
      log.info(m + " is up.")
    }

    // Store the client that answered
    case clientAnswer: ClientAnswer => {
      if (client == null) {
        // A client answered
        log.info("Found a client.")
        client = sender()
        status = ClientSetup

        // Ask it for training data
        client ! RequestData()
      } else {
        log.debug("Additional client answered.")
      }
    }

    // Receive the audoi transcript for a particular data
    case audioTranscript: AudioTranscript => {
      log.info("Recieved transcript: " + audioTranscript.text)
    }

    case audioData: AudioData => ???
  }

}