package io.stempedia.pictoblox.firebase.login

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.esafirm.imagepicker.features.ImagePicker
import com.esafirm.imagepicker.features.ReturnMode
import com.esafirm.imagepicker.model.Image
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.gson.GsonBuilder
import com.trello.rxlifecycle4.android.lifecycle.kotlin.bindUntilEvent
import com.yalantis.ucrop.UCrop
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import io.stempedia.pictoblox.BuildConfig
import io.stempedia.pictoblox.R
import io.stempedia.pictoblox.databinding.FragTeachersDetailBinding
import io.stempedia.pictoblox.util.PictobloxLogger
import io.stempedia.pictoblox.util.fireStorageUserThumb
import io.stempedia.pictoblox.util.firebaseUserDetail
import java.io.File
import java.util.*
import kotlin.math.log

class TeacherDetailFragment : Fragment() {
    private lateinit var mBinding: FragTeachersDetailBinding
    private val vm by activityViewModels<TeacherDetailVM>()
    private var popupWindow: PopupWindow? = null
    private val handler = Handler()
    private val animHelper = PictoBloxActionButtonAnimHelper(lifecycle)
    private val genderArray = arrayOf("Male", "Female", "Other", "Not Disclose")
    val datePickerFrag = PictoBloxDatePickerFragment()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mBinding = DataBindingUtil.inflate(inflater, R.layout.frag_teachers_detail, container, false)
        mBinding.data = vm
        Log.e("TAG", "onCreateView: ", )
        return mBinding.root
    }

    fun onDateSelected(day: Int, month: Int, year: Int) {

        val calendar = Calendar.getInstance()
        calendar.set(year, month, day)

        vm.inputBirthDateSelected.onNext(Timestamp(calendar.timeInMillis / 1000, 0))

    }

    override fun onPause() {
        super.onPause()
        Log.e("TAG", "onPause: ", )
    }

    override fun onStop() {
        super.onStop()
        Log.e("TAG", "onStop: ", )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.e("TAG", "onDestroyView: ", )
    }

    override fun onResume() {
        super.onResume()
        Log.e("TAG", "onResume: ", )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e("TAG", "onDestroy: ", )
    }

    override fun onStart() {
        super.onStart()
        Log.e("TAG", "onStart: ", )
        attachListener()
    }

    fun attachListener(){
        vm.outputChooseBirthDate
            .bindUntilEvent(this, Lifecycle.Event.ON_STOP)
            .subscribe {
                Log.e("TAG", "outputChooseBirthDate clicked: ", )

                datePickerFrag.arguments = it
                datePickerFrag.show(childFragmentManager, "datePickerFrag")
            }


        vm.outputShowGenderDialog
            .bindUntilEvent(this, Lifecycle.Event.ON_STOP)
            .subscribe { genderSelected ->
                val previous = genderArray.indexOf(genderSelected)
                AlertDialog.Builder(requireContext())
                    .setTitle("Select Your Age")
                    .setSingleChoiceItems(genderArray, previous) { d, which ->
                        vm.inputGenderSelected.onNext(genderArray[which])
                        d.dismiss()
                    }
                    .create()
                    .show()
            }

        vm.outputShowToast
            .bindUntilEvent(this, Lifecycle.Event.ON_STOP)
            .subscribe {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }

        vm.outputShowCreatingAccountAnimation
            .bindUntilEvent(this, Lifecycle.Event.ON_STOP)
            .subscribe { startAnimation ->
                if (startAnimation) {
                    animHelper.buttonToProgress(mBinding.button14) {}

                } else {
                    animHelper.progressToButton(mBinding.button14) {}
                }
            }


        vm.outputSignUpCompleted
            .bindUntilEvent(this, Lifecycle.Event.ON_STOP)
            .subscribeWith(object : io.reactivex.rxjava3.observers.DisposableObserver<SignUpCompleteResponse>() {
                override fun onNext(t: SignUpCompleteResponse) {
                    val activityVM by activityViewModels<LoginActivityViewModel>()
                    activityVM.inputStage2Completed.onNext(t)
                }

                override fun onError(e: Throwable) {
                    Toast.makeText(requireContext(), "Server error: please check your connectivity", Toast.LENGTH_LONG).show()
                }

                override fun onComplete() {

                }

            })

        vm.outputThumbClicked
            .bindUntilEvent(this, Lifecycle.Event.ON_STOP)
            .subscribe {
                ImagePicker.create(this)
                    .returnMode(ReturnMode.ALL) // set whether pick and / or camera action should return immediate result or not.
                    .toolbarImageTitle("Select Profile picture") // image selection title
                    .toolbarArrowColor(Color.WHITE) // Toolbar 'up' arrow color
                    .includeVideo(false) // Show video on image picker
                    .single() // single mode
                    .showCamera(true) // show camera or not (true by default)
                    .imageDirectory("Camera") // directory name for captured image  ("Camera" folder by default)
                    .enableLog(false) // disabling log
                    .theme(R.style.PurpleToolbar)
                    .start() // start image picker activity with request code
            }


        vm.outputOpenCropper
            .bindUntilEvent(this, Lifecycle.Event.ON_STOP)
            .subscribe { uCrop ->
                uCrop.start(requireContext(), this)
            }

        mBinding.ccpAdultLogin
            .setOnCountryChangeListener {
                vm.inputCountrySelected.onNext(mBinding.ccpAdultLogin.selectedCountryName)
            }

        vm.inputCountrySelected.onNext(mBinding.ccpAdultLogin.selectedCountryName)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (ImagePicker.shouldHandle(requestCode, resultCode, data)) {
            // or get a single image only
            val image = ImagePicker.getFirstImageOrNull(data)
            vm.inputHandleCroppedImage.onNext(Uri.parse("file://${image.path}"))

            vm.inputOnImageChosen.onNext(image)

        } else if (requestCode == UCrop.REQUEST_CROP) {
            data?.also {
                if (resultCode == Activity.RESULT_OK) {
                    val path = UCrop.getOutput(it)
                    vm.inputHandleCroppedImage.onNext(path!!)

                } else if (resultCode == UCrop.RESULT_ERROR) {
                    Toast.makeText(requireContext(), "Error in processing image", Toast.LENGTH_LONG).show()
                    UCrop.getError(data)?.also { t ->
                        PictobloxLogger.getInstance().logException(t)
                    }
                }
            }
        }
    }

}

class TeacherDetailVM(application: Application) : AndroidViewModel(application) {

    private var birthDateTimeStamp = Timestamp.now()
    val birthDate = ObservableField<String>("Birth Date")
    val gender = ObservableField<String>("Gender")
    val key = ObservableField<String>("")
    val showProgress = ObservableBoolean()
    val profileImageBitmap = ObservableField<Bitmap>()
    private var imagePath: Uri? = null
    private var country = ""

    val name = ObservableField<String>()
    val school = ObservableField<String>()
    val subject = ObservableField<String>()
    val boardOrAffiliation = ObservableField<String>()

    val keyHint = ObservableField<Spannable>()

    val inputBirthDateClicked: PublishSubject<String> = PublishSubject.create<String>()
    val outputChooseBirthDate: Observable<Bundle> = inputBirthDateClicked
        .map {
            Bundle().apply {
                putParcelable("b_day_timestamp", birthDateTimeStamp)
            }
        }
    val inputBirthDateSelected: PublishSubject<Timestamp> = PublishSubject.create<Timestamp>()

    val inputGenderClicked: PublishSubject<String> = PublishSubject.create<String>()
    val outputShowGenderDialog: Observable<String> = inputGenderClicked.map {
        gender.get()!!
    }

    val inputGenderSelected: PublishSubject<String> = PublishSubject.create<String>()
    val inputCountrySelected: PublishSubject<String> = PublishSubject.create<String>()

    val inputSubmitClicked: PublishSubject<String> = PublishSubject.create<String>()

    val outputShowToast: PublishSubject<String> = PublishSubject.create<String>()

    val outputShowCreatingAccountAnimation: PublishSubject<Boolean> = PublishSubject.create<Boolean>()

    val outputSignUpCompleted: Observable<SignUpCompleteResponse> = inputSubmitClicked
        .filter {
            if (birthDate.get() == "Birth Date") {
                outputShowToast.onNext("Please select your Birth Date")
                return@filter false
            }

            if (country.isEmpty()) {
                outputShowToast.onNext("Please select country")
                return@filter false
            }

            if (gender.get() == "Gender") {
                outputShowToast.onNext("Please select your gender.")
                return@filter false
            }

            if (TextUtils.isEmpty(school.get())) {
                outputShowToast.onNext("Please enter school name.")
                return@filter false
            }

            if (TextUtils.isEmpty(subject.get())) {
                outputShowToast.onNext("Please enter subject(s).")
                return@filter false
            }

            if (TextUtils.isEmpty(boardOrAffiliation.get())) {
                outputShowToast.onNext("Please enter Board or Affiliation.")
                return@filter false
            }

            return@filter true
        }
        .doOnNext {
            outputShowCreatingAccountAnimation.onNext(true)
            showProgress.set(true)
        }
        .observeOn(Schedulers.io())
        .map {
            val uid = FirebaseAuth.getInstance().currentUser!!.uid

            if (imagePath != null) {
                val thumbUploadTask = fireStorageUserThumb(uid).putFile(imagePath!!)
                Tasks.await(thumbUploadTask)
            }

            val userDetailUpdateTask = firebaseUserDetail(uid)
                .update(
                    mapOf(
                        "birthdate" to birthDateTimeStamp,
                        "server" to BuildConfig.SERVER,
                        "country" to country,
                        "key" to key.get(),
                        "gender" to gender.get(),
                        "is_secondary_detail_filled" to true,
                        "name" to name.get(),
                        "school" to school.get(),
                        "subject" to subject.get(),
                        "board_of_affiliation" to boardOrAffiliation.get()
                    )
                )

            Tasks.await(userDetailUpdateTask)

            val task = FirebaseFunctions.getInstance("asia-east2")
                .getHttpsCallable("completeSignUpEntry")
                .call()

            val res = Tasks.await(task)

            GsonBuilder().create().fromJson(res.data as String, SignUpCompleteResponse::class.java)
        }
        .observeOn(AndroidSchedulers.mainThread())
        .doOnNext {
            outputShowCreatingAccountAnimation.onNext(false)
            showProgress.set(false)
        }
        .doOnError {
            outputShowCreatingAccountAnimation.onNext(false)
            showProgress.set(false)
        }

    val outputThumbClicked: PublishSubject<String> = PublishSubject.create<String>()

    val inputOnImageChosen: PublishSubject<Image> = PublishSubject.create<Image>()

    val outputOpenCropper: Observable<UCrop> = inputOnImageChosen
        .map { image ->
            val file = File.createTempFile("ProfilePic", ".png", application.cacheDir)

            UCrop.of(Uri.parse("file://${image.path}"), Uri.fromFile(file))
                .withAspectRatio(10f, 10f)
                .withMaxResultSize(300, 300)
        }

    val inputHandleCroppedImage: PublishSubject<Uri> = PublishSubject.create<Uri>()

    override fun onCleared() {
        super.onCleared()
        Log.e("TAG", "onCleared: ", )
    }

    init {
        Log.e("TAG", "init: ", )
        val builder = SpannableStringBuilder()
        builder.append("Key")

        val pst = SpannableString("(optional)")
        pst.setSpan(RelativeSizeSpan(0.75f), 0, pst.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        builder.append(pst)

        keyHint.set(builder)
        inputBirthDateSelected
            .subscribe {
                birthDateTimeStamp = it

                val date = it.toDate()
                val day = DateFormat.format("dd", date).toString().toInt()
                val month = DateFormat.format("MMM", date).toString()
                val year = DateFormat.format("yyyy", date).toString().toInt()

                birthDate.set("$day/$month/$year")
            }

        inputGenderSelected
            .subscribe {
                gender.set(it)
            }

        inputCountrySelected
            .subscribe { selectedCountryName ->
                selectedCountryName?.also {
                    country = it
                }
            }

        inputHandleCroppedImage
            .subscribe { path ->

                imagePath = path

                Glide.with(application)
                    .asBitmap()
                    .apply(RequestOptions().circleCrop())
                    .load(path)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onLoadCleared(placeholder: Drawable?) {
                        }

                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            profileImageBitmap.set(resource)
                        }
                    })
            }

        val src = imagePath ?: R.drawable.ic_account3

        Glide.with(application)
            .asBitmap()
            .apply(RequestOptions().circleCrop())
            .load(src)
            .into(object : CustomTarget<Bitmap>() {
                override fun onLoadCleared(placeholder: Drawable?) {

                }

                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    profileImageBitmap.set(resource)
                }
            })
    }
}