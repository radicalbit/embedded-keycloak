package org.tmt.embedded_keycloak.impl

import os.{proc, SubProcess}
import OsLibExtensions._

import scala.util.Try

class StopHandle private[embedded_keycloak] (subProcess: SubProcess) {
  def stop(): Unit = {
    val process: Process = subProcess.wrapped.asInstanceOf[java.lang.Process]

    getPidOfProcess(process).foreach(pid => getAllChildPids(pid).foreach(killPid))

    subProcess.destroyForcibly()
  }

  private def killPid(pid: Long): Unit = proc("kill", "-9", pid).call()

  private def getAllChildPids(pid: Long): List[Long] =
    proc("pgrep", "-P", pid).call().output.map(_.trim.toLong).toList

  private def getPidOfProcess(p: Process): Option[Long] = {
    if (p.getClass.getName == "java.lang.UNIXProcess") {
      Try {
        val f = p.getClass.getDeclaredField("pid")
        f.setAccessible(true)
        val pid = f.getLong(p)
        f.setAccessible(false)
        pid
      }.toOption
    } else None
  }
}
