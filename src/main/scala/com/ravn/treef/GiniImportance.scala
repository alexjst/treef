package com.ravn.treef

import java.util

/**
 * Created by remim on 29/04/14.
 */
class GiniImportance(val featuresList : List[Feature]) {

  def perform(ensemble : Ensemble, dataPoints : List[DataPoint]) = {

    val result = collection.mutable.Map() ++ featuresList.map(f => (f, 0d))

    ensemble.trees
      .map(t => (meanDecrease(t, dataPoints), t.weight))
      .foreach(e => e._1.foreach(f => result(f._1) = result(f._1) + f._2 * e._2))

    result.toMap
  }

  // result is mean decrease for each feature for one tree


  def meanDecrease(tree: Tree, dataPoints: List[DataPoint]) : Map[Feature, Double] = {

    // for each node, we need the number of time it has been called, and the number of positive
    val catPath : List[(Int, List[TreeNode])] = dataPoints.map(d => (d.c, tree.root.evaluate(d)))

    val positives = catPath.filter(_._1 > 0).map(_._2).flatten.groupBy(identity).mapValues(_.size)
    val totals = catPath.map(_._2).flatten.groupBy(identity).mapValues(_.size)

    val nodeCount : Map[TreeNode, (Int, Int)] =
      // concatenate with duplicates
      (totals.toSeq ++ positives.toSeq)
      // group by node
      .groupBy(_._1)
      // only keep counts
      .mapValues(_.map(_._2))
      // and transform seq of counts in a tuple
      .map(e => (e._1, e._2 match {
        case Seq(t,p) => (t,p)
        case Seq(t) => (t,0)
      }))


    // we calculate the Gini impurity of all nodes

    val giniImpurity: ((Int, Int)) => Double = { case (t,p) =>
      1 - math.pow(p.toFloat/t, 2) - math.pow((t-p).toFloat/t, 2)
    }

    val nodeImpurity = nodeCount.map(e => (e._1, giniImpurity(e._2)))


    // then the impurity decrease
    val getCount = (n:TreeNode) => {
      if (nodeCount.contains(n))
        nodeCount(n)._1
      else 0
    }

    val getImpurity = (n:TreeNode) => {
      if (nodeImpurity.contains(n))
        nodeImpurity(n)
      else 0
    }

    def impurityDecrease(node : Split) : Double = {
      val total   = nodeCount(node)._1
      val pLeft   = getCount(node.left).toFloat / total
      val pRight  = getCount(node.right).toFloat / total

      nodeImpurity(node)
        - pLeft * getImpurity(node.left)
        - pRight * getImpurity(node.right)
    }

    val decreases = nodeCount.keys
      .filter(n => n.isInstanceOf[Split])
      .asInstanceOf[Iterable[Split]]
      .map(n => (n, impurityDecrease(n)))



    /*def getCount(c : TreeNode) : Double =
      if (nodeCount.contains(c)) nodeCount(c).toDouble
      else 0d

    def squareProba(c : TreeNode, n: Int) : Double =
      math.pow(getCount(c).toFloat / n, 2)

    // from that we can calculate the Gini impurity of each node
    def impurity(node : TreeNode, n : Int) : Double = {
      node match {
        case l : Leaf => 0d
        case s : Split => 1 - squareProba(s.left, n) - squareProba(s.right, n)
      }
    }

    val nodeCountImp : Map[TreeNode, (Int, Double)] =
      nodeCount.map(e => (e._1, (e._2, impurity(e._1, e._2))))


    // and the Gini decrease for each split node
    def impurProba(c : TreeNode, n : Int) : Double = {
      if (nodeCountImp.contains(c)) {
        val e = nodeCountImp(c)
        e._1 * e._2 / n
      } else 0d
    }

    def decrease(node : Split, n : Int, i : Double) : Double = {
      val r = i - impurProba(node.left, n) - impurProba(node.right, n)
      if (r < 0)
        return 0d
//        println("here")

      r
    }

    val splitDecrease = nodeCountImp
      .filterKeys(n => n.isInstanceOf[Split])
      .asInstanceOf[Map[Split, (Int, Double)]]
      .map(e => (e._1, decrease(e._1, e._2._1, e._2._2)))*/


    // and finally we sum them per feature
    decreases.groupBy(_._1.feature).map(e => (e._1, e._2.toMap.values.sum / e._2.size))
  }

  // steps : gini impurity for each node / gini decrease for each node / get all nodes for a particular feature
  // gini impurity : proba

  def normalize(results: Map[Feature, Double]) : Map[Feature, Int] = {
    val m = results.values.reduceLeft(_ max _)
    results.map(e => (e._1, (e._2*100/m).toInt))
  }


  def print(results : Map[Feature, Double]) = {
    val printer = (f: Feature, i: Int) => println(f.label + " " + i)
    normalize(results).toList.sortBy(_._2).reverse.foreach(printer.tupled)
  }
}
