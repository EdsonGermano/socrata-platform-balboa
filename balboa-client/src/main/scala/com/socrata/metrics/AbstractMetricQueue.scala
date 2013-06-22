package com.socrata.metrics

import com.socrata.balboa.metrics.Metric
import org.apache.commons.lang.StringUtils
import java.net.URL
import java.util.Date
import com.socrata.metrics.MetricQueue.{AccessChannel, Action}
import scala.math.Numeric

abstract class AbstractMetricQueue extends MetricQueue {

  def create[T <: Number](entity:IdParts, name: IdParts, value:T) {
    create(entity, name, value.longValue(), new Date().getTime)
  }

  def create(entity: IdParts, name: IdParts, value:Long) {
    create(entity, name, value, new Date().getTime, Metric.RecordType.AGGREGATE)
  }

  def create(entity: IdParts, name:IdParts, value:Long, timestamp: Long) {
    create(entity, name, value, timestamp, Metric.RecordType.AGGREGATE)
  }

  // TODO: Convert display type to enum
  def logViewChildLoaded(parentViewUid:ViewUid, childViewUid:ViewUid, displaytype: String) {
    if (displaytype == null || displaytype.isEmpty) {
      create(MetricIdParts(Fluff("children-loaded-"), parentViewUid), MetricIdParts(Fluff("filter-"), childViewUid), 1)
    }
    else if (displaytype == "map") {
      create(MetricIdParts(Fluff("children-loaded-"), parentViewUid), MetricIdParts(Fluff("map-" ), childViewUid), 1)
    }
    else if (displaytype == "chart") {
      create(MetricIdParts(Fluff("children-loaded-"), parentViewUid), MetricIdParts(Fluff("chart-"), childViewUid), 1)
    }
  }

  def logViewSearch(view:ViewUid, query:QueryString) {
    create(MetricIdParts(Fluff("searches-"), view), MetricIdParts(Fluff("search-"), query), 1)
  }

  def logUserSearch(domainId:DomainId, query:QueryString) {
    create(MetricIdParts(Fluff("searches-"), domainId), MetricIdParts(Fluff("users-search-"), query), 1)
  }

  def logDatasetSearch(domainId:DomainId, query:QueryString) {
    create(MetricIdParts(Fluff("searches-"), domainId), MetricIdParts(Fluff("datasets-search-"), query), 1)
  }

  def logUserCreated(domainId:DomainId) {
    create(domainId, Fluff("users-created"), 1)
  }

  def logAppTokenCreated(domainId:DomainId) {
    create(domainId, Fluff("app-token-created"), 1)
  }

  def logAppTokenRequest(tokenUid:AppToken, domainId:DomainId, ip:Ip) {
    create(Fluff("ip-applications"), MetricIdParts(Fluff("application-"), tokenUid, Fluff("-"),  ip), 1)
    create(MetricIdParts(domainId, Fluff("-applications")), MetricIdParts(Fluff("application-"),tokenUid), 1)
    create(MetricIdParts(Fluff("applications")), MetricIdParts(Fluff("application-"), tokenUid), 1)
    create(MetricIdParts(Fluff("application-"),tokenUid), MetricIdParts(Fluff("requests")), 1)
  }

  def logAppTokenRequestForView(viewUid:ViewUid, token:AppToken) {
    create(MetricIdParts(Fluff("view-"), viewUid, Fluff("-apps")), MetricIdParts(if (token == null) Fluff("anon") else token), 1)
  }

  def logApiQueryForView(viewUid:ViewUid, query:QueryString) {
    create(MetricIdParts(Fluff("view-"), viewUid,Fluff("-query")), MetricIdParts(if (query == null || (query.query == "")) Fluff("select *") else query), 1)
    create(viewUid, Fluff("queries-served"), 1)
  }

  def logSocrataAppTokenUsed(ip:Ip) {
    // TODO? Shouldn't this have an implementation?
  }

  def logMapCreated(domainId:DomainId, parentUid:ViewUid) {
    create(domainId, Fluff("maps-created"), 1)
    create(parentUid, Fluff("maps-created"), 1)
  }

  def logMapDeleted(domainId:DomainId, parentViewUid:ViewUid) {
    create(domainId, Fluff("maps-deleted"), 1)
    create(parentViewUid, Fluff("maps-deleted"), 1)
  }

  def logChartCreated(domainId:DomainId, parentViewUid:ViewUid) {
    create(domainId, Fluff("charts-created"), 1)
    create(parentViewUid, Fluff("charts-created"), 1)
  }

  def logChartDeleted(domainId:DomainId, parentViewUid:ViewUid) {
    create(domainId, Fluff("charts-deleted"), 1)
    create(parentViewUid, Fluff("charts-deleted"), 1)
  }

  def logFilteredViewCreated(domainId:DomainId, parentViewUid:ViewUid) {
    create(domainId, Fluff("filters-created"), 1)
    create(parentViewUid, Fluff("filters-created"), 1)
  }

  def logFilteredViewDeleted(domainId:DomainId, parentViewUid:ViewUid) {
    create(domainId, Fluff("filters-deleted"), 1)
    create(parentViewUid, Fluff("filters-deleted"), 1)
  }

  def logDatasetCreated(domainId:DomainId, viewUid:ViewUid, isBlob: Boolean, isHref: Boolean) {
    if (isBlob) {
      create(domainId, Fluff("datasets-created-blobby"), 1)
    }
    if (isHref) {
      create(domainId, Fluff("datasets-created-href"), 1)
    }
    create(domainId, Fluff("datasets-created"), 1)
  }

  def logDatasetDeleted(domainId:DomainId, viewUid:ViewUid, isBlob: Boolean, isHref: Boolean) {
    if (isBlob) {
      create(domainId, Fluff("datasets-deleted-blobby"), 1)
    }
    if (isHref) {
      create(domainId, Fluff("datasets-deleted-href"), 1)
    }
    create(domainId, Fluff("datasets-deleted"), 1)
  }

  def logRowsCreated(count: Int, domainId:DomainId, viewUid:ViewUid, token:AppToken) {
    create(domainId, Fluff("rows-created"), count)
    create(viewUid, Fluff("rows-created"), count)
  }

  def logRowsDeleted(count: Int, domainId:DomainId, viewUid:ViewUid, token:AppToken) {
    create(domainId, Fluff("rows-deleted"), count)
    create(viewUid, Fluff("rows-deleted"), count)
    logAppTokenOnView(viewUid, token)
  }

  def logDatasetReferrer(referrer:ReferrerUri, viewUid:ViewUid) {
    try {
      val url:URL = new URL(referrer.referrer)
      val host: String = url.getProtocol + "-" + url.getHost
      var path: String = url.getPath
      if (!StringUtils.isBlank(url.getQuery)) {
        path += "?" + url.getQuery
      }
      create(MetricIdParts(Fluff("referrer-hosts-"), viewUid), MetricIdParts(Fluff("referrer-"), Host(host)), 1)
      create(MetricIdParts(Fluff("referrer-paths-"), viewUid, Fluff("-"), Host(host)), MetricIdParts(Fluff("path-"), Path(path)), 1)
    }
  }

  def logPublish(referrer:ReferrerUri, viewUid:ViewUid, domainId:DomainId) {
    try {
      val url: URL = new URL(referrer.referrer)
      val host: String = url.getProtocol + "-" + url.getHost
      var path: String = url.getPath
      if (!StringUtils.isBlank(url.getQuery)) {
        path += "?" + url.getQuery
      }
      create(MetricIdParts(Fluff("publishes-uids-"), domainId),MetricIdParts(Fluff("uid-"), viewUid), 1)
      create(MetricIdParts(Fluff("publishes-hosts-"), viewUid),MetricIdParts(Fluff("referrer-"), Host(host)), 1)
      create(MetricIdParts(Fluff("publishes-paths-"), viewUid, Fluff("-"), Host(host)),MetricIdParts(Fluff("path-"), Path(path)), 1)
      create(MetricIdParts(Fluff("publishes-hosts-"), domainId),MetricIdParts(Fluff("referrer-"), Host(host)), 1)
      create(MetricIdParts(Fluff("publishes-paths-"), domainId , Fluff("-"), Host(host)),MetricIdParts(Fluff("path-"), Path(path)), 1)
    }
  }


  //(actionType: com.socrata.metrics.MetricQueue.Action.Value, viewUid: com.socrata.metrics.ViewUid, domainId: com.socrata.metrics.DomainId, value: Integer, tokenUid: com.socrata.metrics.AppToken)Unit
  def logAction(t:MetricQueue.Action.Value, viewUid:ViewUid, domainId:DomainId, value:Int, tokenUid:AppToken) {
    val v:Int =
    if (value == null) {
      1
    } else {
      value
    }
    if (t eq Action.RATING) {
      create(viewUid, Fluff("ratings-total"), v)
      create(viewUid, Fluff("ratings-count"), 1)
      create(domainId, Fluff("ratings-total"), v)
      create(domainId, Fluff("ratings-count"), 1)
    }
    else if (t eq Action.VIEW) {
      create(viewUid, Fluff("view-loaded"), 1)
      create(domainId, Fluff("view-loaded"), 1)
      create(MetricIdParts(Fluff("views-loaded-"), domainId), MetricIdParts(Fluff("view-") , viewUid), 1)
    }
    else {
      create(viewUid, Fluff(t.toString + "s"), value)
      create(domainId, Fluff(t.toString + "s"), value)
    }
    logAppTokenOnView(viewUid, tokenUid)
  }

  def logBytesInOrOut(inOrOut: String, viewUid:ViewUid, domainId:DomainId, bytes: Long) {
    if (viewUid != null) {
      create(MetricIdParts(viewUid), MetricIdParts(Fluff("bytes-" + inOrOut)), bytes)
    }
    create(domainId, Fluff("bytes-" + inOrOut), bytes)
  }

  def logRowAccess(accessType:MetricQueue.AccessChannel.Value, domainId:DomainId, viewUid:ViewUid, count: Int, token:AppToken) {
    create(viewUid, Fluff("rows-accessed-" + accessType), 1)
    create(domainId, Fluff("rows-accessed-" + accessType), 1)
    if (AccessChannel.DOWNLOAD eq accessType) {
      create(MetricIdParts(Fluff("views-downloaded-"), domainId), MetricIdParts(Fluff("view-"), viewUid), 1)
    }
    create(viewUid, Fluff("rows-loaded-" + accessType), count)
    create(domainId, Fluff("rows-loaded-" + accessType), count)
    logAppTokenOnView(viewUid, token)
  }

  def logGeocoding(domainId:DomainId, viewUid:ViewUid, count: Int) {
    create(viewUid, Fluff("geocoding-requests"), count)
    create(domainId, Fluff("geocoding-requests"), count)
  }

  def logDatasetDiskUsage(timestamp: Long, viewUid:ViewUid, authorUid:UserUid, bytes: Long) {
    create(viewUid, Fluff("disk-usage"), bytes, timestamp, Metric.RecordType.ABSOLUTE)
    create(MetricIdParts(Fluff("user-"), authorUid, Fluff("-views-disk-usage")), MetricIdParts(Fluff("view-"), viewUid), bytes, timestamp, Metric.RecordType.ABSOLUTE)
  }

  def logDomainDiskUsage(timestamp: Long, domainId:DomainId, bytes: Long) {
    create(domainId, Fluff("disk-usage"), bytes, timestamp, Metric.RecordType.ABSOLUTE)
  }

  def logUserDomainDiskUsage(timestamp: Long, userUid:UserUid, domainId:DomainId, bytes: Long) {
    create(MetricIdParts(domainId, Fluff("-users-disk-usage")), MetricIdParts(Fluff("user-"), userUid), bytes, timestamp, Metric.RecordType.ABSOLUTE)
  }

  private def logAppTokenOnView(realViewUid:ViewUid, token:AppToken) {
    if (token != null) {
      create(MetricIdParts(realViewUid, Fluff("-apps")), MetricIdParts(token), 1)
      create(MetricIdParts(Fluff("app-"), token), MetricIdParts(realViewUid), 1)
    }
  }
}