package com.mindcandy.spark

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf

object DockerMain {
  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("Docker Spark").setMaster("local[*]")
    val sc = new SparkContext(conf)
    val NUM_SAMPLES = 10
    val count = sc.parallelize(1 to NUM_SAMPLES).map { i =>
      val x = Math.random()
      val y = Math.random()
      if (x * x + y * y < 1) 1 else 0
    }.reduce(_ + _)
    println("Pi is roughly " + 4.0 * count / NUM_SAMPLES)
  }
}