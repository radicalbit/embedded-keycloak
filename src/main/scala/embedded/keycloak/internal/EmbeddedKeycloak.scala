package embedded.keycloak.internal

import akka.actor.ActorSystem
import embedded.keycloak.data.DataFeeder
import embedded.keycloak.internal.Bash._
import embedded.keycloak.models.{KeycloakData, Settings}
import os.Path

import scala.concurrent.{ExecutionContext, Future}

class EmbeddedKeycloak(
    keycloakData: KeycloakData,
    settings: Settings = Settings.default)(implicit actorSystem: ActorSystem) {

  private val installer =
    new Installer(settings, keycloakData)

  private val healthCheck = new HealthCheck(settings)
  private val ports = new Ports()
  private val dataFeeder = new DataFeeder(settings, keycloakData)

  import settings._

  private def getBinDirectory =
    Path(installationDirectory) / version / s"binaries" / s"keycloak-$version.Final" / "bin"

  def preRun(): Unit = {
    installer.install()
    ports.checkAvailability(port = port, `throw` = true)
  }

  def startServer()(implicit ec: ExecutionContext): Unit = {
    preRun()
    exec(
      s"sh ${getBinDirectory / "standalone.sh"} " +
        s"-Djboss.bind.address=$host " +
        s"-Djboss.http.port=$port")
  }

  def startServerInBackground()(
      implicit ec: ExecutionContext): Future[StopHandle] = {
    preRun()

    val process = background(
      s"sh ${getBinDirectory / "standalone.sh"} " +
        s"-Djboss.bind.address=$host " +
        s"-Djboss.http.port=$port")

    val stopHandle = new StopHandle(process)

    healthCheck
      .checkHealth()
      .map(_ => dataFeeder.feed())
      .map(_ => stopHandle)
  }
}
