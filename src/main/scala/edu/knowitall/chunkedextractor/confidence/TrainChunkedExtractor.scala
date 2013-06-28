package edu.knowitall.chunkedextractor.confidence

import java.io.File
import edu.knowitall.common.Resource
import scala.io.Source
import edu.knowitall.chunkedextractor.Relnoun
import edu.knowitall.tool.chunk.OpenNlpChunker
import edu.knowitall.tool.stem.MorphaStemmer
import edu.knowitall.common.Analysis
import edu.knowitall.tool.conf.BreezeLogisticRegressionTrainer
import edu.knowitall.chunkedextractor.BinaryExtractionInstance
import edu.knowitall.tool.conf.Labelled

object TrainChunkedExtractor extends App {
  case class Config(
    inputFile: File = null,
    outputFile: File = null,
    goldFile: File = null) {
  }

  val parser = new scopt.immutable.OptionParser[Config]("trainer") {
    def options = Seq(
      arg("<sentence-file>", "sentences") { (path: String, config: Config) =>
        val file = new File(path)
        require(file.exists(), "file does not exist: " + path)
        config.copy(inputFile = file)
      },
      arg("<gold-file>", "gold") { (path: String, config: Config) =>
        val file = new File(path)
        require(file.exists(), "file does not exist: " + path)
        config.copy(goldFile = file)
      },
      arg("<output-file>", "output") { (path: String, config: Config) =>
        val file = new File(path)
        require(!file.exists(), "file already exist: " + path)
        config.copy(outputFile = file)
      })
  }

  parser.parse(args, Config()) match {
    case Some(config) => run(config)
    case None =>
  }

  def run(config: Config) = {
    val relnoun = new Relnoun()

    val chunker = new OpenNlpChunker()

    val gold = Resource.using(Source.fromFile(config.goldFile)) { goldSource =>
      goldSource.getLines.map(_.split("\t") match {
        case Array(label, arg1, rel, arg2) => (arg1, rel, arg2) -> (label == "1")
      }).toMap
    }
    val examples =
      Resource.using(Source.fromFile(config.inputFile)) { source =>
        for {
          line <- source.getLines.toList
          chunked = chunker(line) map MorphaStemmer.lemmatizePostaggedToken

          inst <- relnoun.extract(chunked)

          extr = inst.extr
          label = gold(extr.arg1.text, extr.rel.text, extr.arg2.text)
        } yield {
          new Labelled(label, inst)
        }
      }

    val trainer = new BreezeLogisticRegressionTrainer(ChunkedExtractorFeatureSet)
    val trained = trainer.train(examples)

    trained.saveFile(config.outputFile)
  }
}