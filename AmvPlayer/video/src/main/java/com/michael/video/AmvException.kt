/**
 * 例外クラス
 *
 * @author M.TOYOTA 2018.07.26 Created
 * Copyright © 2018 M.TOYOTA  All Rights Reserved.
 */
package com.michael.video

class AmvException @JvmOverloads constructor(msg:String, cause:Throwable?=null) : Exception(msg,cause)