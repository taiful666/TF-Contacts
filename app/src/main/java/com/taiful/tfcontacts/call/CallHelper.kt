package com.taiful.tfcontacts.call

import android.annotation.SuppressLint
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast

object CallHelper {

    fun telecom(context: Context): TelecomManager = context.getSystemService(TelecomManager::class.java)

    @SuppressLint("MissingPermission")
    fun simAccounts(context: Context): List<PhoneAccountHandle> = try {
        telecom(context).callCapablePhoneAccounts.orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    /** Operator name for a SIM (e.g. "Grameenphone"), falling back to the system label. */
    @SuppressLint("MissingPermission")
    fun accountLabel(context: Context, h: PhoneAccountHandle): String {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val tm = context.getSystemService(android.telephony.TelephonyManager::class.java)
                val subId = tm.getSubscriptionId(h)
                val sm = context.getSystemService(android.telephony.SubscriptionManager::class.java)
                val info = sm.getActiveSubscriptionInfo(subId)
                val name = info?.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: info?.displayName?.toString()
                if (!name.isNullOrBlank()) return name
            }
            val subs = context.getSystemService(android.telephony.SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
            subs.firstOrNull { it.subscriptionId.toString() == h.id || (it.iccId != null && it.iccId == h.id) }?.let { s ->
                val n = s.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: s.displayName?.toString()
                if (!n.isNullOrBlank()) return n
            }
        } catch (_: Exception) {
        }
        return try {
            telecom(context).getPhoneAccount(h)?.label?.toString() ?: "SIM"
        } catch (_: Exception) {
            "SIM"
        }
    }

    @SuppressLint("MissingPermission")
    fun defaultAccount(context: Context): PhoneAccountHandle? = try {
        telecom(context).getDefaultOutgoingPhoneAccount("tel")
    } catch (_: Exception) {
        null
    }

    /** True when the user has to choose a SIM before calling. */
    fun needsSimChoice(context: Context): Boolean = simAccounts(context).size > 1 && defaultAccount(context) == null

    @SuppressLint("MissingPermission")
    fun place(context: Context, number: String, account: PhoneAccountHandle? = null) {
        val clean = number.trim()
        if (clean.isEmpty()) return
        val uri = Uri.fromParts("tel", clean, null)
        try {
            val extras = Bundle()
            if (account != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
            telecom(context).placeCall(uri, extras)
        } catch (e: SecurityException) {
            try {
                context.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                Toast.makeText(context, "Please allow phone calls permission", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun sms(context: Context, number: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "No messaging app found", Toast.LENGTH_SHORT).show()
        }
    }

    fun isDefaultDialer(context: Context): Boolean = try {
        telecom(context).defaultDialerPackage == context.packageName
    } catch (_: Exception) {
        false
    }

    fun defaultDialerIntent(activity: Activity): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = activity.getSystemService(RoleManager::class.java) ?: return null
            if (!rm.isRoleAvailable(RoleManager.ROLE_DIALER)) null
            else rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else {
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
        }
    }

    data class CallerInfo(val name: String?, val photo: String?)

    fun lookup(context: Context, number: String?): CallerInfo {
        if (number.isNullOrBlank()) return CallerInfo(null, null)
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI), null, null, null
            )?.use { if (it.moveToFirst()) CallerInfo(it.getString(0), it.getString(1)) else null } ?: CallerInfo(null, null)
        } catch (_: Exception) {
            CallerInfo(null, null)
        }
    }
}

@Suppress("DEPRECATION")
fun Call.st(): Int = if (Build.VERSION.SDK_INT >= 31) details.state else state

fun Call.number(): String? = details?.handle?.schemeSpecificPart
