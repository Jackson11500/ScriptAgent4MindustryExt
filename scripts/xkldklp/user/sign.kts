@file:Depends("coreLibrary/DBApi")
@file:Depends("wayzer/user/userService")

package xkldklp.user

import coreLibrary.DBApi.DB.registerTable

registerTable(SignEntity.T)