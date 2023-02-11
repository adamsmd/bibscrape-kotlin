package org.michaeldadams.bibscrape

import com.pinterest.ktlint.core.Rule
import com.pinterest.ktlint.core.ast.ElementType
import com.pinterest.ktlint.ruleset.standard.StringTemplateRule
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

class LongStringTemplateRule : Rule("long-string-template") {
  val stringTemplateRule = StringTemplateRule()
  override fun beforeVisitChildNodes(
    node: ASTNode,
    autoCorrect: Boolean,
    emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit
  ) {
    if (node.elementType == ElementType.SHORT_STRING_TEMPLATE_ENTRY) {
      emit(node.treePrev.startOffset + 1, "Missing curly braces", false)
    }

    stringTemplateRule.beforeVisitChildNodes(node, autoCorrect) {
        offset, errorMessage, canBeAutoCorrected ->
      if (errorMessage != "Redundant curly braces") {
        emit(offset, errorMessage, canBeAutoCorrected)
      }
    }
  }
}
