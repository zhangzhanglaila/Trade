package hash

import (
	"crypto/md5"
	"encoding/hex"
)

var _md5 = md5.New()

func Md5Hex(data []byte) string {
	bytes := _md5.Sum(data)

	return hex.EncodeToString(bytes)
}
