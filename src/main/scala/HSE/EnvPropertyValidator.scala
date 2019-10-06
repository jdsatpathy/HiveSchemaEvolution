package HSE

import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.apache.spark.sql.SparkSession


class EnvPropertyValidator (spark : SparkSession, envProp : Config, genericValidator : GenericValidator , logger : Logger) {
  def envPropValidate(spark : SparkSession, envProp : Config, genericValidator : GenericValidator) {

    try {
      genericValidator.isValidPath(spark, envProp.getString("input")) match {
        case true => println("valid path")
        case _ => println("Not a valid path")
      }
    } catch {
      case e: Exception => println("isValidPath input: "+e.getMessage)
    }

    try {
      genericValidator.isString(envProp.getString("ordersFile")) match {
        case true => println(s"valid string : ${envProp.getString("ordersFile")}")
        case _ => println(s"Not a valid string : ${envProp.getString("ordersFile")}")
      }
    }
    catch {
      case e: Exception => println(s"isString ordersFile : ${e.getMessage}")
    }

    try {
      genericValidator.isValidPath(spark, envProp.getString("output")) match {
        case true => println(s"Valid path : ${envProp.getString("output")}")
        case _ => println(s"Not a valid string : ${envProp.getString("output")}")
      }
    }
    catch {
      case e: Exception => println("isValidPath output :"+e.getMessage)
    }

    try {
      genericValidator.isValidPath(spark, envProp.getString("sparkwarehouse")) match {
        case true => println(s"Valid Path : ${envProp.getString("sparkwarehouse")}")
        case _ => println(s"Not a valid path : " + envProp.getString("sparkwarehouse"))
      }
    }
    catch {
      case e: Exception => println("isValidPath sparkwarehouse : " + e.getMessage)
    }

    try {
      genericValidator.isString(envProp.getString("hivemetastore")) match {
        case true => println(s"Valid String : ${envProp.getString("hivemetastore")}")
        case _ => println("Not a valid String : ")
      }
    }
    catch {
      case e: Exception => println("isString hivemetastore : " + e.getMessage)
    }

    try {
      genericValidator.isValidPath(spark, envProp.getString("ordersschemafile")) match {
        case true => println(s"Valid path : ${envProp.getString("ordersschemafile")}")
        case _ => println(s"Not a valid string : ${envProp.getString("ordersschemafile")}")
      }
    }
    catch {
      case e: Exception => println("isValidPath ordersschemafile : "+e.getMessage)
    }

    try {
      genericValidator.isValidHiveDB(spark, envProp.getString("hive.database")) match {
        case true => println(s"Valid database" + envProp.getString("hive.database"))
        case _ => println("Not a valid path : " + envProp.getString("hive.database"))
      }
    }
    catch {
      case e: Exception => println("isValidHiveDB hive.database : "+e.getMessage)
    }

    try {
      genericValidator.isValidHiveTable(spark, envProp.getString("hive.database"), envProp.getString("ordersStgTbl")) match {
        case true => println(s"Valid table" + envProp.getString("ordersStgTbl"))
        case _ => println(s"Valid table" + envProp.getString("ordersStgTbl"))
      }
    } catch {
      case e: Exception => println("isValidHiveTable hive.database : "+e.getMessage)
    }
  }
}
