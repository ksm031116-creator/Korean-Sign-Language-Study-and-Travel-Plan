package app.signkorean.adapter.content

import android.util.Log
import app.signkorean.domain.content.ContentIssue

/** 콘텐츠 검사 결과를 logcat 에 남긴다. 오류는 error, 나머지는 warn. */
object LogcatContentIssueReporter : ContentIssueReporter {
    private const val TAG = "SignKoreanContent"

    override fun report(issues: List<ContentIssue>) {
        issues.forEach { issue ->
            if (issue.isError) Log.e(TAG, issue.toString()) else Log.w(TAG, issue.toString())
        }
    }
}
