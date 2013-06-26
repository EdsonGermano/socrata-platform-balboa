package com.socrata.metrics

import com.socrata.metrics.migrate._
import com.socrata.balboa.metrics.Metric.RecordType
import java.util.{TimeZone, GregorianCalendar, Date}
import com.socrata.balboa.metrics.data.{Period, DateRange, DataStoreFactory}
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import scala.collection.JavaConverters._


class MigrationOperation(_entity:IdParts, _name:IdParts, _t:RecordType) {
  def getEntity =  _entity
  def getName = _name
  def getRecordType = _t
  def hasPart(p:MetricIdPart) = {
    _entity.hasPart(p) || _name.hasPart(p)
  }
  def isChildOf(op:MigrationOperation) = {
    getEntity.getParts != null && getEntity.getParts.exists { e:IdParts => e.toString().contains(op.getName.toString())}
  }
  def isParent() = {
    _name.getParts != null && _name.getParts.size == 1 && _name.isUnresolved()
  }
  def isUnresolved() = {
    _entity.isUnresolved() || _name.isUnresolved()
  }

  def replacePart(in:MetricIdPart, out:MetricIdPart) = {
    MetricOperation(getEntity.replacePart(in, out), getName.replacePart(in, out), getRecordType)
  }
}
case class MetricOperation(entity:IdParts, name:IdParts, t: RecordType) extends MigrationOperation(entity, name, t)
case class ResolvedMetricOperation(entity:IdParts, name:IdParts, t: RecordType) extends MigrationOperation(entity, name, t)
case class ParentMetricOperation(entity:IdParts, name:IdParts, t: RecordType, children:Seq[MetricOperation]) extends MigrationOperation(entity, name, t)

/** Read one fully resolved metric and write it to another metric **/
case class ReadWriteOperation(read:MigrationOperation, write:MigrationOperation, value:Option[Long], time:Date = new Date(), period:Period = Period.FIFTEEN_MINUTE) extends MigrationOperation(read.getEntity, read.getName, read.getRecordType) {
  def getValue():Option[Long] =  value
  def getTime() = time
}
/** Read one fully resolved /entity/ for all the metric names and produce ReadWriteOperations for the children **/
case class ReadChildrenOperation(parent:ParentMetricOperation) extends MigrationOperation(parent.entity, parent.name, parent.t)


object Migrator {
  var log:Log = LogFactory.getLog("Migrator")
  def syncViewMetrics(viewUid:ViewUid, destViewUid:ViewUid, domainId:DomainId, start:Date, end:Date, dryrun:Boolean) = {
       // Record the operations associated with all log*(viewUid) calls
       log.info("======== Recording ========")
       val recording = new MetricRecord().recordViews(viewUid, domainId)
       recording.foreach(log.trace(_))
       // Convert the recording into a list of Migration Read* operations
       log.info("======== Pass One ======== ")
       val passOne = recording flatMap { op:MigrationOperation => new ResolvedMetricToReadWrite(new ViewUidMetricTransform(viewUid, destViewUid)).apply(op) }
       passOne.foreach(log.trace(_))
       // transform all ReadChildrenOperations into ReadWrite by actually reading the parent entity/metric
       log.info("======== Pass Two ========")
       val passTwo = passOne flatMap { op:MigrationOperation => new ResolveChildrenToReadWrite(DataStoreFactory.get(), start, end).apply(op) }
       passTwo.foreach(log.trace(_))
       // Convert the operations, once more into read/write operations using the specified transform
       log.info("======== Pass Three ========")
       val passThree = passTwo flatMap { op:MigrationOperation => new ResolvedMetricToReadWrite(new ViewUidMetricTransform(viewUid, destViewUid)).apply(op) }
       passThree.foreach(log.trace(_))
       log.info("======== Calculating Yearly ========")
       val yearlies = passThree.par flatMap {
         y:MigrationOperation => new ExpandToRange(Period.YEARLY, start, end).apply(y)
       }
       log.info("======== Calculating Monthly ========")
       val monthlies = yearlies.par flatMap {
         y:MigrationOperation => new ExpandOpToPeriod(Period.MONTHLY).apply(y)
       }
       log.info("======== Calculating Weekly ========")
       val weeklies = monthlies.par flatMap {
         y:MigrationOperation => new ExpandOpToPeriod(Period.WEEKLY).apply(y)
       }
       log.info("======== Calculating Daily ========")
       val dailies = weeklies.par flatMap {
         y:MigrationOperation => new ExpandOpToPeriod(Period.DAILY).apply(y)
       }
       log.info("======== Calculating Hourly ========")
       val hourlies = dailies.par flatMap {
         y:MigrationOperation => new ExpandOpToPeriod(Period.HOURLY).apply(y)
       }
       log.info("======== Calculating Fifteen Minutely ========")
       val deltas = hourlies.par flatMap {
         y:MigrationOperation => new ExpandOpToPeriod(Period.FIFTEEN_MINUTE).apply(y)
       }
       log.info("======== Writing Metrics ========")

       deltas flatMap {
         d:MigrationOperation => new WriteMetric(DataStoreFactory.get(), dryrun).apply(d)
       }
   }

   def main(args: Array[String]) {
      if (args.length < 4) {
        println("Migrator [src view 4x4] [dst view 4x4] [domain id] [dryrun|real]")
        System.exit(1)
      }
      val start = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
      start.set(2009, 1, 1, 0, 0, 0)
      val end = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
      end.set(2014, 1, 1, 0, 0, 0)

      val stuff = syncViewMetrics(ViewUid(args(0)), ViewUid(args(1)), DomainId(args(2).toInt), start.getTime, end.getTime, !"real".equals(args(3)))
     stuff.foreach {
       m => println(m)
     }
   }
}


