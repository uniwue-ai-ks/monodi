import sys.process._

val user = "root"
val host = "corpus-monodicum.de"
val address = s"$user@$host"
val serverBackupDir = "/root/monodi_backups"
val getAvailableBackupsCommand = s"ls -1 $serverBackupDir"
val postgresLoginString = "postgresql://postgres:test@localhost"

def exportDokument(name: String, uuid: String): Unit = {
  println("dropping old database");
  ("echo 'drop database if exists tmpbackup;'" #| s"psql $postgresLoginString/postgres").!
  println("create new database");
  ("echo 'create database tmpbackup;'" #| s"psql $postgresLoginString/postgres").!
  println(s"copying backup $name");
  s"scp $address:$serverBackupDir/$name /tmp/mdbackup".!!
  println(s"unzipping backup $name");
  "unzip -o /tmp/mdbackup -d /tmp/".!!
  println("loading backup into database");
  ("cat /tmp/-" #| s"psql $postgresLoginString/tmpbackup").!
  val jsonFileName = s"/tmp/${name}.json"
  val jsonFile = new java.io.File(jsonFileName)
  println(s"exporting file $jsonFileName");
  ((List("echo", s"select notes from dokument where id = '$uuid';") #| s"psql -qtAX $postgresLoginString/tmpbackup") #> jsonFile).!
  println(s"finished backup $name")
  "sleep 3s".!
}

@main
def main(uuid: String): Unit = {
  val available_backups = s"ssh $address $getAvailableBackupsCommand".!!.linesIterator.toList


  for {
    backup <- available_backups
  } {
    exportDokument(backup, uuid)
  }
}
