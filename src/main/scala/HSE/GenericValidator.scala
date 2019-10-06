package HSE

import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.log4j.Logger
import org.apache.spark.sql.{DataFrame, SparkSession}

import scala.util.matching.Regex

class GenericValidator {
  def isInteger(value : Any): Boolean=
  {


true
  }

  def isString(value : Any) : Boolean = {
    true
  }

  def isOfLength (str : String, len : Int) : Boolean ={
    if (str.length == len)
      true
    else
      false
  }

  def isValidPath (spark : SparkSession, path : String) : Boolean ={
    val fs = FileSystem.get(spark.sparkContext.hadoopConfiguration)
    if ((fs.exists(new Path(path))) || (fs.isFile(new Path(path)))) {
      true
    }
    else
    {
      false
    }
  }

  def isValidHiveDB (spark : SparkSession, dbName : String) : Boolean = {
    var returnStr = true
    try{
      spark.sql(s"use ${dbName}")
    }
    catch
      {
        case e : Exception => println(s"Not a valid database : ${dbName}")
          returnStr = false
        //Logger.getLogger("org").error(s"Not a valid database : ${dbName}")
      }
    returnStr
  }

  def isValidHiveTable (spark : SparkSession, dbName : String, tblName : String) : Boolean = {
    var returnStr = true
    try{
      spark.sql(s"describe ${dbName}.${tblName}")
    }
    catch
      {
        case e : Exception => println(s"Not a valid table : ${dbName}")
          returnStr = false
        //Logger.getLogger("org").error(s"Not a valid database : ${dbName}")
      }
    returnStr
  }
//dimension modeling (data modelling)
  //Python, spark structured streaming
}
