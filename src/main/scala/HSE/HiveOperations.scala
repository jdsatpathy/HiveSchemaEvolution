package HSE
//import java.util.logging.Logger

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark.sql.types.{IntegerType, StringType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, SparkSession}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import com.typesafe.scalalogging.Logger

object HiveOperations {
  def main(args: Array[String]): Unit = {
    val logger = Logger("Root")

    val envProp: Config = ConfigFactory.load().getConfig(args(0))
    //To modify getSparkSession()
    val spark = SparkSession.builder().appName("Hive Schema Evolution Support")
      .master(args(1))
      .config("hive.metastore.uris", envProp.getString("hivemetastore"))
      .config("spark.sql.warehouse.dir", envProp.getString("sparkwarehouse"))
      .enableHiveSupport().getOrCreate()


    val misdate = args(2).toString
    val hiveDatabase = envProp.getString("hive.database")
    val ordersSchemaFilePath = envProp.getString("ordersschemafile")
    val orderSrcFileName = envProp.getString("ordersFile") + "-" + misdate
    val ordersStgTblName = envProp.getString("ordersStgTbl")
    //exclude print and include logger
    //Logger.getLogger("org").setLevel(Level.ERROR)
    logger.info(orderSrcFileName)
    //avoid using map
    val genericValidator = new GenericValidator()
    genericValidator.isOfLength(misdate,8) match {
      case true => logger.info("Valid misdate")
      case _ => logger.info("Invalid misdate")
    }
    try {
      /* ##############    Validate inputs through application.properties file    ###############*/
      val envPropertyValidator = new EnvPropertyValidator(spark, envProp, genericValidator, logger)
      envPropertyValidator.envPropValidate(spark, envProp, genericValidator)
      /*#########################################################################################*/
      val ordersDf = this.readOrdersToDF(spark, ordersSchemaFilePath, envProp, orderSrcFileName, logger)
      val nullEliminatedDf = this.nullValidationInPkFk(ordersDf, spark)
      logger.info("nullEliminatedDf : " +nullEliminatedDf.count())
      val deDuplicatedDf = this.removeDuplicatePkFk(nullEliminatedDf, spark, "orderId", "orderCustomerId", "orderDate")
      logger.info("deDuplicatedDf : "+ deDuplicatedDf.count())
      this.rejectTrack(deDuplicatedDf,ordersDf,envProp,"orders")
      this.hiveStagingTableInsertion(deDuplicatedDf,spark,hiveDatabase, ordersStgTblName, logger)
    }catch {
      case e : Exception => logger.info("In main method: "+e.getMessage + e.getStackTrace)
    }

  }

  def rejectTrack(finalDf : DataFrame, initialDf : DataFrame, envProp : Config, rejectFileName : String) : Unit = {
    val rejectDir = rejectFileName + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
    val fs = FileSystem.get(new Configuration())
    if(fs.exists(new Path(envProp.getString("output") + "reject/" + rejectDir))) fs.delete(new Path(envProp.getString("output") + "reject/" + rejectDir))
    initialDf.except(finalDf).write.csv(envProp.getString("output") + "reject/" + rejectDir)
  }

  def nullValidationInPkFk(df : DataFrame, spark : SparkSession) : DataFrame = {
    import spark.implicits._
    df.filter(($"orderId" isNotNull) && ($"orderCustomerId" isNotNull))
  }

  def removeDuplicatePkFk(df : DataFrame, spark : SparkSession, primaryCol1 : String, primaryCol2 : String, secondaryCol1 : String) : DataFrame = {
    val tempViewNameForDuplicateRemove = "tempViewNameForDuplicateRemove"+ DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
    df.createOrReplaceTempView(tempViewNameForDuplicateRemove)
    spark.sql(s" select od.* from "+
      s"(select ${primaryCol1}, ${primaryCol2}, max(${secondaryCol1}) ${secondaryCol1} "+
      s"from ${tempViewNameForDuplicateRemove} group by ${primaryCol1} , ${primaryCol2} ) a "+
      s"join ${tempViewNameForDuplicateRemove} od "+
      s" on "+
      s"a.${primaryCol1} = od.${primaryCol1} and "+
      s"a.${primaryCol2} = od.${primaryCol2} and "+
      s"a.${secondaryCol1} = od.${secondaryCol1} ")

  }

  def removeDupicates(df : DataFrame, spark : SparkSession, allColumns : String, duplicateCheckColumns : String) : DataFrame = {
  df.createOrReplaceTempView("tempTableRemDup")
   //Use scala
    spark.sql(s"select ${allColumns} from (select ${allColumns}, " +
      s"count(*) cnt from tempTableRemDup group by ${duplicateCheckColumns})")
  }

  //Takes the dataframe and the column name from which the time stamp is to be removed and returns
  //dataframe without the timestamp
  def removeTimestamp(df : DataFrame, spark : DataFrame, colName : String) : DataFrame = {
  spark
  }

  //Read the incremental file into a dataframe
  def readOrdersToDF(spark : SparkSession, schemaPath : String, envProp : Config, ordersFileName : String, logger : Logger): DataFrame = {
      val schemaFile: Iterator[String] = scala.io.Source.fromFile(schemaPath).getLines()
      val schemaField: Array[StructField] = schemaFile.toArray.map(l => {
        val arr = l.split(":")
        arr(1) match {
          case "int" => StructField(arr(0), IntegerType, true)
          case _ => StructField(arr(0), StringType, true)
        }
      })
      val dfSchema = StructType(schemaField)
    logger.info("Orders incremental file read: " + envProp.getString("input") + ordersFileName)
    logger.info(dfSchema.toString())

    val df =  spark.read.schema(dfSchema).csv(envProp.getString("input") + ordersFileName)
    logger.info("Df read count: " +df.count())
    df
  }

  def getAllColumnNamesCommaSeparated(schemaFilePath : String) : String = {
    val ordersColumnsLst = scala.io.Source.fromFile(schemaFilePath).getLines()
      .map(m => {
        m.split(":")(0)
      }).toList.toString
    ordersColumnsLst.substring(5,ordersColumnsLst.length-1)

  }

  def hiveStagingTableInsertion(df : DataFrame, spark : SparkSession, hiveDB : String, stgTbl : String, logger : Logger) : Boolean = {
    val sparkTmpViewName = "sparkTmpViewName" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now)
    df.createOrReplaceTempView(sparkTmpViewName)
    logger.info("Count of Dataframe to be inserted: " + df.count().toInt)
    spark.sql(s"truncate table ${hiveDB}.${stgTbl}")
    spark.sql(s"insert into ${hiveDB}.${stgTbl} select * from ${sparkTmpViewName}")
    val sparkTmpViewCount = spark.sql(s"select count(1) from ${sparkTmpViewName}").first().toString.stripPrefix("[").stripSuffix("]").trim.toInt
    logger.info("Spark view count: "+sparkTmpViewCount)
    val hiveStgTblCount = spark.sql(s"select count(1) from ${hiveDB}.${stgTbl}").first().toString.stripPrefix("[").stripSuffix("]").trim.toInt
    logger.info("Hive staging table count: "+hiveStgTblCount)
    if (sparkTmpViewCount == hiveStgTblCount) true else false
  }

//  def envPropValidate(spark : SparkSession, envProp : Config, genericValidator : GenericValidator) {
//
//    try {
//      genericValidator.isValidPath(spark, envProp.getString("input")) match {
//        case true => logger.info("valid path")
//        case _ => logger.info("Not a valid path")
//      }
//    } catch {
//      case e: Exception => logger.info("isValidPath input: "+e.getMessage)
//    }
//
//    try {
//      genericValidator.isString(envProp.getString("ordersFile")) match {
//        case true => logger.info(s"valid string : ${envProp.getString("ordersFile")}")
//        case _ => logger.info(s"Not a valid string : ${envProp.getString("ordersFile")}")
//      }
//    }
//    catch {
//      case e: Exception => logger.info(s"isString ordersFile : ${e.getMessage}")
//    }
//
//    try {
//      genericValidator.isValidPath(spark, envProp.getString("output")) match {
//        case true => logger.info(s"Valid path : ${envProp.getString("output")}")
//        case _ => logger.info(s"Not a valid string : ${envProp.getString("output")}")
//      }
//    }
//    catch {
//      case e: Exception => logger.info("isValidPath output :"+e.getMessage)
//    }
//
//    try {
//      genericValidator.isValidPath(spark, envProp.getString("sparkwarehouse")) match {
//        case true => logger.info(s"Valid Path : ${envProp.getString("sparkwarehouse")}")
//        case _ => logger.info(s"Not a valid path : " + envProp.getString("sparkwarehouse"))
//      }
//    }
//    catch {
//      case e: Exception => logger.info("isValidPath sparkwarehouse : " + e.getMessage)
//    }
//
//    try {
//      genericValidator.isString(envProp.getString("hivemetastore")) match {
//        case true => logger.info(s"Valid String : ${envProp.getString("hivemetastore")}")
//        case _ => logger.info("Not a valid String : ")
//      }
//    }
//    catch {
//      case e: Exception => logger.info("isString hivemetastore : " + e.getMessage)
//    }
//
//    try {
//      genericValidator.isValidPath(spark, envProp.getString("ordersschemafile")) match {
//        case true => logger.info(s"Valid path : ${envProp.getString("ordersschemafile")}")
//        case _ => logger.info(s"Not a valid string : ${envProp.getString("ordersschemafile")}")
//      }
//    }
//    catch {
//      case e: Exception => logger.info("isValidPath ordersschemafile : "+e.getMessage)
//    }
//
//    try {
//      genericValidator.isValidHiveDB(spark, envProp.getString("hive.database")) match {
//        case true => logger.info(s"Valid database" + envProp.getString("hive.database"))
//        case _ => logger.info("Not a valid path : " + envProp.getString("hive.database"))
//      }
//    }
//    catch {
//      case e: Exception => logger.info("isValidHiveDB hive.database : "+e.getMessage)
//    }
//
//    try {
//      genericValidator.isValidHiveTable(spark, envProp.getString("hive.database"), envProp.getString("ordersStgTbl")) match {
//        case true => logger.info(s"Valid table" + envProp.getString("ordersStgTbl"))
//        case _ => logger.info(s"Valid table" + envProp.getString("ordersStgTbl"))
//      }
//    } catch {
//      case e: Exception => logger.info("isValidHiveTable hive.database : "+e.getMessage)
//    }
//  }



}
