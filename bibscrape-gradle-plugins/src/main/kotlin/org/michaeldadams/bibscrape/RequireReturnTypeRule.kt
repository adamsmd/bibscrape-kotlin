package org.michaeldadams.bibscrape

import com.pinterest.ktlint.core.Rule
import com.pinterest.ktlint.core.ast.ElementType
import com.pinterest.ktlint.ruleset.standard.StringTemplateRule
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

class RequireReturnTypeRule : Rule("require-return-type") {
  val stringTemplateRule = StringTemplateRule()
  override fun beforeVisitChildNodes(
    node: ASTNode,
    autoCorrect: Boolean,
    emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit
  ) {
    if (node.elementType == ElementType.FUN) {
      val children = node.getChildren(null)
      val index = children.indexOfLast { it.elementType == ElementType.COLON }
      if (index < 0 ||
        index + 2 > children.lastIndex ||
        children[index + 1].elementType != ElementType.WHITE_SPACE ||
        children[index + 2].elementType != ElementType.TYPE_REFERENCE) {
        emit(node.startOffset, "Missing return type on function", false)
      }
    }
  }
}
